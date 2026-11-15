package com.iot.platform.mqtt;

import com.iot.platform.config.IotProperties;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订阅 Broker 内部的 PUBLISH 事件，把遥测数据接进业务链路。
 *
 * <p>这里有一条容易被忽略的安全校验：主题里的 deviceKey 必须等于 CONNECT 时的 username。
 * 没有这一步，设备 A 只要知道设备 B 的 key，就能往 B 的主题里灌假数据——
 * 而 Broker 的 ACL 只校验"能不能发这个主题"，不会替你校验"这个主题是不是你的"。
 *
 * <p>回调跑在 Netty IO 线程上，所以只做解析和拷贝，真正的入库丢给线程池。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryInterceptor extends AbstractInterceptHandler {

    private final IotProperties props;
    private final TelemetryIngestService ingestService;

    @Override
    public String getID() {
        return "telemetry-ingest";
    }

    @Override
    public Class<?>[] getInterceptedMessageTypes() {
        return new Class<?>[]{InterceptPublishMessage.class, InterceptConnectMessage.class};
    }

    @Override
    public void onConnect(InterceptConnectMessage msg) {
        log.info("设备已连接 clientId={} username={}", msg.getClientID(), msg.getUsername());
    }

    @Override
    public void onPublish(InterceptPublishMessage msg) {
        try {
            String topic = msg.getTopicName();
            String username = msg.getUsername();

            // iot/{productKey}/{deviceKey}/telemetry
            String[] parts = topic.split("/");
            if (parts.length != 4) {
                return;
            }
            if (!props.getMqtt().getTopicPrefix().equals(parts[0]) || !"telemetry".equals(parts[3])) {
                return;
            }
            String productKey = parts[1];
            String topicDeviceKey = parts[2];

            if (!topicDeviceKey.equals(username)) {
                log.warn("设备 {} 试图向 {} 上报，主题归属不符，已丢弃", username, topic);
                return;
            }

            // ByteBuf 在回调返回后会被 Broker 释放，必须先拷贝出来
            var payload = msg.getPayload();
            byte[] bytes = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), bytes);

            ingestService.ingestAsync(username, productKey, bytes);
        } catch (Exception e) {
            // 绝不能让异常冒到 Netty 线程
            log.error("处理 MQTT 消息失败 topic={}", msg.getTopicName(), e);
        }
    }

    /**
     * Moquette 0.17 把这个方法加进了接口，但 AbstractInterceptHandler 没给空实现，
     * 不覆写就编译不过。语义是"会话处理循环里出错了"，记一条日志即可。
     */
    @Override
    public void onSessionLoopError(Throwable error) {
        log.error("MQTT 会话循环异常", error);
    }
}
