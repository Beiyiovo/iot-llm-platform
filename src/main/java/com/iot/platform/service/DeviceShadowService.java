package com.iot.platform.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备影子：内存里保存每台设备的最新指标快照。
 *
 * <p>用途有两个：看板要"实时值"时不用回查时序表（那是全表扫最慢的一类查询）；
 * 做 AI 诊断时先把最新状态拼进提示词，模型才知道设备现在什么情况。
 *
 * <p>换个角度说：时序表回答"过去发生了什么"，影子回答"现在是什么样"。
 */
@Component
public class DeviceShadowService {

    public record Shadow(Long deviceId,
                         String deviceKey,
                         Map<String, Double> metrics,
                         LocalDateTime lastSeenAt,
                         long updates) {
    }

    private final Cache<String, Shadow> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    private final AtomicLong counter = new AtomicLong();

    public void update(Long deviceId, String deviceKey, Map<String, Double> metrics, LocalDateTime ts) {
        cache.asMap().compute(deviceKey, (k, old) -> {
            java.util.Map<String, Double> merged = new java.util.LinkedHashMap<>();
            if (old != null) {
                merged.putAll(old.metrics());
            }
            merged.putAll(metrics);
            return new Shadow(deviceId, deviceKey, Map.copyOf(merged), ts, counter.incrementAndGet());
        });
    }

    public Shadow get(String deviceKey) {
        return cache.getIfPresent(deviceKey);
    }

    public Collection<Shadow> all() {
        return cache.asMap().values();
    }

    public long totalUpdates() {
        return counter.get();
    }
}
