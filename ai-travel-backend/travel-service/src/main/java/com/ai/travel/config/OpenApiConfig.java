package com.ai.travel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 旅行规划后端 API")
                        .version("1.0.0")
                        .description("AI 智能旅行规划后端服务接口文档")
                        .contact(new Contact()
                                .name("AI Travel Team")
                                .email("support@aitravel.com")));
    }
}
