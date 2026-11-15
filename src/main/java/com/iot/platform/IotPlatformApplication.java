package com.iot.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IoT 设备接入与智能诊断平台启动类。
 *
 * <p>启动后同时提供三样东西：
 * <ul>
 *     <li>HTTP 接口（8080），Knife4j 文档在 /doc.html</li>
 *     <li>内嵌 MQTT Broker（1883），设备直接连上来上报遥测</li>
 *     <li>规则告警引擎 + 大模型诊断</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.iot.platform.mapper")
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(com.iot.platform.config.IotProperties.class)
public class IotPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotPlatformApplication.class, args);
    }
}
