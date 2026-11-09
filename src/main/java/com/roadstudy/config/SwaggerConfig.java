package com.roadstudy.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {
    @Bean
    OpenAPI roadstudyOpenApi() {
        return new OpenAPI().info(new Info().title("RoadStudy API").version("v1"));
    }
}
