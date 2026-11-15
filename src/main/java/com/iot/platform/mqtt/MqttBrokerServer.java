package com.iot.platform.mqtt;

import com.iot.platform.config.IotProperties;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.InterceptHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

/**
 * 把 Moquette 内嵌进 Spring Boot 生命周期。
 *
 * <p>为什么不用 EMQX / Mosquitto：面试时最常被问的一句是"别人 clone 下来怎么跑"。
 * 外置 Broker 意味着对方要装 Docker 或装服务，多一步就少一半人跑通。
 * Moquette 是纯 Java 的 Broker，跟着 JVM 一起起，clone + mvn spring-boot:run 就完事。
 *
 * <p>代价是它不适合生产（单机、无集群、无自带 Dashboard），所以 README 里写清楚：
 * 这个选型是给"可验证的 demo"服务的，不是给"生产部署"服务的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttBrokerServer implements SmartLifecycle {

    private final IotProperties props;
    private final DeviceAuthenticator authenticator;
    private final TelemetryInterceptor telemetryInterceptor;

    private Server server;
    private volatile boolean running = false;

    @Override
    public void start() {
        if (!props.getMqtt().isEnabled()) {
            log.info("MQTT Broker 已在配置中关闭（iot.mqtt.enabled=false）");
            return;
        }
        Properties p = new Properties();
        p.setProperty(IConfig.HOST_PROPERTY_NAME, props.getMqtt().getHost());
        p.setProperty(IConfig.PORT_PROPERTY_NAME, String.valueOf(props.getMqtt().getPort()));
        p.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, String.valueOf(props.getMqtt().isAllowAnonymous()));
        // 不做持久化：demo 场景重启丢消息可以接受，换来的是零外部依赖
        p.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
        // Moquette 即使关了持久化，也要往 data_path 写一个 uuid 文件；指到临时目录避免污染项目根
        p.setProperty("data_path", System.getProperty("java.io.tmpdir") + java.io.File.separator + "moquette");

        IConfig config = new MemoryConfig(p);
        server = new Server();
        try {
            server.startServer(config,
                    List.<InterceptHandler>of(telemetryInterceptor),
                    null,
                    authenticator,
                    null);
            running = true;
            log.info("内嵌 MQTT Broker 已启动 tcp://{}:{}（设备主题 {}/{{productKey}}/{{deviceKey}}/telemetry）",
                    props.getMqtt().getHost(), props.getMqtt().getPort(), props.getMqtt().getTopicPrefix());
        } catch (Exception e) {
            // 端口被占用是最常见的失败原因，给出可操作的提示而不是一串堆栈
            running = false;
            log.error("MQTT Broker 启动失败（端口 {} 可能被占用）：{}",
                    props.getMqtt().getPort(), e.getMessage());
            throw new IllegalStateException("MQTT Broker 启动失败，请检查端口 " + props.getMqtt().getPort(), e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            try {
                server.stopServer();
                log.info("内嵌 MQTT Broker 已停止");
            } catch (Exception e) {
                log.warn("停止 MQTT Broker 时出错", e);
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 最后启动、最先停止：保证 Brokers 存在时业务 Bean 都还活着 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
