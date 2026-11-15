package com.iot.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台自有配置，对应 application.yml 里的 iot.* 节点。
 */
@Data
@ConfigurationProperties(prefix = "iot")
public class IotProperties {

    private Mqtt mqtt = new Mqtt();
    private Jwt jwt = new Jwt();
    private Alarm alarm = new Alarm();
    private Ai ai = new Ai();

    @Data
    public static class Mqtt {
        private boolean enabled = true;
        private String host = "0.0.0.0";
        private int port = 1883;
        private String username;
        private String password;
        private boolean allowAnonymous = false;
        private String topicPrefix = "iot";
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expireMinutes = 720;
        private String header = "Authorization";
        private String prefix = "Bearer ";
    }

    @Data
    public static class Alarm {
        private int counterCacheSize = 10000;
    }

    @Data
    public static class Ai {
        private boolean enabled = true;
        private String provider = "mock";
        private String model = "qwen-plus";
        private String apiKey;
        private int timeoutSeconds = 12;
        private int sampleSize = 30;
    }
}
