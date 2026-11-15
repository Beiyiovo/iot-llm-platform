package com.iot.platform.simulator;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备模拟器：用真的 MQTT 客户端连真的 Broker，走真的 TCP。
 *
 * <p>不是直接往数据库塞数据——那样演示的时候你自己都没底。
 * 这里的三台设备会连上 1883，按 deviceKey/secret 认证，往
 * {@code iot/{productKey}/{deviceKey}/telemetry} 发 JSON，
 * 后面的解析、入库、告警、诊断全都是真实链路。
 *
 * <p>开启方式：
 * <pre>
 * java -jar iot-llm-platform-1.0.0.jar --iot.simulator.enabled=true
 * </pre>
 * 或者只做端到端验证时把它关掉，用现成的 curl 手发一条。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "iot.simulator.enabled", havingValue = "true")
public class DeviceSimulator implements ApplicationRunner, DisposableBean {

    @Value("${iot.simulator.interval-seconds:5}")
    private int intervalSeconds;

    @Value("${iot.mqtt.port:1883}")
    private int mqttPort;

    private final List<MqttClient> clients = new ArrayList<>();
    private ScheduledExecutorService scheduler;

    /** 每台设备独立计数，否则三台共享一个 tick，异常节奏会乱 */
    private final java.util.Map<String, AtomicLong> ticks = new java.util.concurrent.ConcurrentHashMap<>();

    /** 与 DataInitializer 里的演示设备保持一致 */
    private record Profile(String deviceKey, String secret, String productKey, String name) {
    }

    private static final List<Profile> PROFILES = List.of(
            new Profile("dev_demo_gw01", "a1b2c3d4e5f60718293a4b5c6d7e8f90", "gw", "3号楼网关"),
            new Profile("dev_demo_cold01", "b2c3d4e5f60718293a4b5c6d7e8f90a1", "cold", "冷库温控"),
            new Profile("dev_demo_power01", "c3d4e5f60718293a4b5c6d7e8f90a1b2", "power", "车间配电柜")
    );

    @Override
    public void run(ApplicationArguments args) {
        start();
    }

    public void start() {
        scheduler = Executors.newScheduledThreadPool(PROFILES.size(), r -> {
            Thread t = new Thread(r, "simulator");
            t.setDaemon(true);
            return t;
        });
        for (Profile p : PROFILES) {
            scheduler.scheduleAtFixedRate(() -> emit(p), 0, intervalSeconds, TimeUnit.SECONDS);
        }
        log.info("设备模拟器已启动：{} 台设备，每 {} 秒上报一次", PROFILES.size(), intervalSeconds);
    }

    private void emit(Profile p) {
        try {
            MqttClient client = ensureConnected(p);
            if (client == null) {
                return;
            }
            long t = ticks.computeIfAbsent(p.deviceKey(), k -> new AtomicLong()).incrementAndGet();
            String payload = buildPayload(p.deviceKey(), t);
            String topic = "iot/" + p.productKey() + "/" + p.deviceKey() + "/telemetry";
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            client.publish(topic, msg);
            log.debug("[{}] -> {}", p.name(), payload);
        } catch (Exception e) {
            log.warn("[{}] 上报失败: {}", p.name(), e.getMessage());
        }
    }

    private MqttClient ensureConnected(Profile p) {
        try {
            for (MqttClient c : clients) {
                if (c.getClientId().startsWith(p.deviceKey()) && c.isConnected()) {
                    return c;
                }
            }
            String clientId = p.deviceKey() + "-sim";
            MqttClient client = new MqttClient("tcp://127.0.0.1:" + mqttPort, clientId, new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setUserName(p.deviceKey());
            opts.setPassword(p.secret().toCharArray());
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setConnectionTimeout(5);
            client.connect(opts);
            clients.add(client);
            log.info("[{}] 已连接 Broker，clientId={}", p.name(), clientId);
            return client;
        } catch (Exception e) {
            log.warn("[{}] 连接 Broker 失败: {}", p.name(), e.getMessage());
            return null;
        }
    }

    /**
     * 数据里故意埋了几种形态，方便演示时不用等：
     * <ul>
     *     <li>正常波动 → 平滑曲线</li>
     *     <li>每 40 个周期一次的持续爬升 → 触发"连续 N 点超阈值"</li>
     *     <li>随机单点毛刺 → 证明防抖有效，不会刷告警</li>
     * </ul>
     */
    private String buildPayload(String deviceKey, long t) {
        double noise = (Math.random() - 0.5) * 2;
        return switch (deviceKey) {
            case "dev_demo_gw01" -> {
                double temp = 42 + 3 * Math.sin(t / 8.0) + noise * 1.2;
                // 每 40 个周期，连续 5 个点抬到 66~72，超过 60 的阈值，触发连续 3 点告警
                if (t % 40 >= 30 && t % 40 <= 34) {
                    temp = 66 + noise * 3;
                } else if (t % 40 == 20) {
                    temp = 65; // 单点毛刺：不连续，不应该告警
                }
                double humidity = 55 + 8 * Math.sin(t / 15.0) + noise;
                double co2 = 620 + 80 * Math.sin(t / 20.0) + noise * 10;
                yield String.format("{\"ts\":%d,\"metrics\":{\"temperature\":%.2f,\"humidity\":%.2f,\"co2\":%.1f}}",
                        System.currentTimeMillis(), temp, humidity, co2);
            }
            case "dev_demo_cold01" -> {
                double temp = -22 + 1.5 * Math.sin(t / 10.0) + noise * 0.6;
                // 每 60 个周期化霜一次，温度升到 -12，超过 -15 阈值
                if (t % 60 >= 50 && t % 60 <= 52) {
                    temp = -13 + noise;
                }
                double humidity = 70 + 5 * Math.sin(t / 12.0);
                yield String.format("{\"ts\":%d,\"metrics\":{\"temperature\":%.2f,\"humidity\":%.2f}}",
                        System.currentTimeMillis(), temp, humidity);
            }
            case "dev_demo_power01" -> {
                double current = 85 + 12 * Math.sin(t / 9.0) + noise * 3;
                // 每 50 个周期来一次负载冲击，电流冲到 135，超过 120 阈值
                if (t % 50 >= 42 && t % 50 <= 45) {
                    current = 132 + noise * 5;
                }
                double temp = 45 + 4 * Math.sin(t / 11.0) + noise;
                double voltage = 220 + 2.5 * Math.sin(t / 7.0) + noise * 0.5;
                yield String.format(
                        "{\"ts\":%d,\"metrics\":{\"current\":%.2f,\"temperature\":%.2f,\"voltage\":%.2f}}",
                        System.currentTimeMillis(), current, temp, voltage);
            }
            default -> String.format("{\"ts\":%d,\"metrics\":{\"value\":%.2f}}",
                    System.currentTimeMillis(), 50 + noise);
        };
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (MqttClient c : clients) {
            try {
                if (c.isConnected()) {
                    c.disconnect();
                }
                c.close();
            } catch (Exception ignored) {
                // 关闭时的异常没有处理价值
            }
        }
        log.info("设备模拟器已停止");
    }
}
