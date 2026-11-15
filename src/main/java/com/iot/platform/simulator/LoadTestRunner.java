package com.iot.platform.simulator;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 压测工具：多连接并发往 Broker 打遥测，测"上报 → 入库"这条链路的实际吞吐。
 *
 * <p>不是拿 ab/wrk 打 HTTP 接口——那测的是查询。真正要证明的是**写入链路**：
 * MQTT 解码 → 认证 → 异步线程池 → 批量入库。所以用 N 条 MQTT 连接同时压。
 *
 * <p>用法：
 * <pre>
 * java -jar app.jar --iot.loadtest.enabled=true --iot.loadtest.count=2000 --iot.loadtest.threads=4
 * </pre>
 * 说完数字会自己打出来，跑完 Ctrl+C 即可。
 *
 * <p>注意：这是本机自测，MySQL 和 Broker 同机，数字只代表"这台机器上能跑多快"，
 * 不能当生产基准。写进 README 时也是这么标注的。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iot.loadtest.enabled", havingValue = "true")
public class LoadTestRunner implements ApplicationRunner {

    @Value("${iot.loadtest.count:2000}")
    private int count;

    @Value("${iot.loadtest.threads:4}")
    private int threads;

    @Value("${iot.mqtt.port:1883}")
    private int port;

    private static final String DEVICE_KEY = "dev_demo_gw01";
    private static final String SECRET = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private static final String TOPIC = "iot/gw/dev_demo_gw01/telemetry";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("压测开始：{} 条消息 / {} 个并发连接", count, threads);
        AtomicLong sent = new AtomicLong();
        int perThread = count / threads;
        CountDownLatch latch = new CountDownLatch(threads);
        long start = System.nanoTime();

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try (MqttClient client = new MqttClient("tcp://127.0.0.1:" + port,
                        DEVICE_KEY + "-load-" + idx, new MemoryPersistence())) {
                    MqttConnectOptions opts = new MqttConnectOptions();
                    opts.setUserName(DEVICE_KEY);
                    opts.setPassword(SECRET.toCharArray());
                    opts.setCleanSession(true);
                    opts.setMaxInflight(1000);
                    client.connect(opts);
                    for (int n = 0; n < perThread; n++) {
                        String payload = String.format(
                                "{\"ts\":%d,\"metrics\":{\"temperature\":%.2f,\"humidity\":%.2f,\"co2\":%.1f}}",
                                System.currentTimeMillis(), 42 + Math.random() * 6,
                                55 + Math.random() * 5, 620 + Math.random() * 30);
                        MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                        msg.setQos(0);
                        client.publish(TOPIC, msg);
                        sent.incrementAndGet();
                    }
                    client.disconnect();
                } catch (Exception e) {
                    log.error("压测线程 {} 失败", idx, e);
                } finally {
                    latch.countDown();
                }
            }, "load-" + i);
            workers.add(t);
            t.start();
        }

        latch.await();
        double seconds = (System.nanoTime() - start) / 1e9;

        log.info("发送完成：{} 条消息，耗时 {}s，发送速率 {}/s",
                sent.get(), String.format("%.2f", seconds), String.format("%.0f", sent.get() / seconds));
        log.info("等待异步入库…");
        Thread.sleep(5000);
        log.info("""
                
                ═══════════════════════════════════════════
                 压测结果（本机自测，非生产基准）
                ───────────────────────────────────────────
                 消息数      : {}
                 数据点      : {}  （每条 3 个指标）
                 并发连接    : {}
                 发送耗时    : {} s
                 发送吞吐    : {} 条/秒
                 ═══════════════════════════════════════════
                """,
                sent.get(), sent.get() * 3, threads,
                String.format("%.2f", seconds),
                String.format("%.0f", sent.get() / seconds));
    }
}
