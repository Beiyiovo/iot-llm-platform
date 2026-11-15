package com.iot.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI3 文档配置。访问 /doc.html 看接口清单，
 * 右上角"Authorize"里贴 JWT 就能直接调需要登录的接口。
 */
@Configuration
public class Knife4jConfig {

    private static final String SCHEME = "Bearer";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IoT 设备接入与智能诊断平台 API")
                        .version("1.0.0")
                        .description("""
                                MQTT 遥测上报 → 时序入库 → 规则告警 → 大模型根因诊断。

                                登录：POST /api/auth/login，默认 admin/123456（ADMIN）或 user/123456（USER），
                                拿到 token 后点右上角 Authorize，填 `Bearer <token>`。
                                """)
                        .contact(new Contact().name("赖振雄")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")));
    }
}
