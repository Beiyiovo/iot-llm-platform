package com.iot.platform.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.entity.Device;
import com.iot.platform.service.AlarmEngine;
import com.iot.platform.service.DeviceService;
import com.iot.platform.service.DeviceShadowService;
import com.iot.platform.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 遥测数据的落地流程：解析 → 入库 → 更新影子 → 过告警规则。
 *
 * <p>放在独立线程池里执行，Broker 的 IO 线程只负责把字节数组递过来。
 * 队列满时用 CallerRunsPolicy 回退到调用线程，宁可慢一点也不丢数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryIngestService {

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final DeviceShadowService shadowService;
    private final AlarmEngine alarmEngine;

    private static final int MAX_METRICS_PER_MESSAGE = 32;

    @Async("ingestExecutor")
    public void ingestAsync(String deviceKey, String productKey, byte[] payload) {
        try {
            Device device = deviceService.getByKey(deviceKey);
            if (device == null) {
                log.warn("收到未知设备的上报 deviceKey={}", deviceKey);
                return;
            }

            JsonNode root = objectMapper.readTree(payload);
            Map<String, Double> metrics = parseMetrics(root);
            if (metrics.isEmpty()) {
                log.debug("空上报，忽略 device={}", deviceKey);
                return;
            }
            LocalDateTime ts = parseTs(root);

            telemetryService.ingest(device, metrics, ts);
            shadowService.update(device.getId(), device.getDeviceKey(), metrics, ts);
            deviceService.touch(device.getId(), ts);

            metrics.forEach((metric, value) -> {
                try {
                    alarmEngine.evaluate(device, metric, value, ts);
                } catch (Exception e) {
                    log.error("告警规则计算失败 device={} metric={}", deviceKey, metric, e);
                }
            });
        } catch (Exception e) {
            log.error("遥测入库失败 deviceKey={}", deviceKey, e);
        }
    }

    /**
     * 兼容两种 payload：
     * <pre>
     * {"ts": 1731657600000, "metrics": {"temperature": 25.3, "humidity": 60}}
     * {"temperature": 25.3, "humidity": 60}
     * </pre>
     */
    private Map<String, Double> parseMetrics(JsonNode root) {
        JsonNode node = root.has("metrics") && root.get("metrics").isObject() ? root.get("metrics") : root;
        Map<String, Double> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext() && result.size() < MAX_METRICS_PER_MESSAGE) {
            Map.Entry<String, JsonNode> e = it.next();
            if ("ts".equals(e.getKey()) || !e.getValue().isNumber()) {
                continue;
            }
            double v = e.getValue().asDouble();
            if (Double.isFinite(v)) {
                result.put(e.getKey(), v);
            }
        }
        return result;
    }

    /**
     * 支持秒 / 毫秒两种时间戳；不传就用服务端时间。
     */
    private LocalDateTime parseTs(JsonNode root) {
        long raw = root.path("ts").asLong(0);
        if (raw <= 0) {
            return LocalDateTime.now();
        }
        long millis = raw < 1_000_000_000_000L ? raw * 1000 : raw;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
