package com.iot.platform.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 本地缓存。设备影子（最新状态）放这里，避免每次查时序表。
 * 没装 Redis 也能跑，装了 Redis 换一个 CacheManager 就行——这就是把缓存抽成接口的好处。
 */
@Configuration
public class CacheConfig {

    public static final String DEVICE_SHADOW = "deviceShadow";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(DEVICE_SHADOW);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats());
        return manager;
    }
}
