package dev.assessment.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI apiDescription() {
        return new OpenAPI().info(new Info()
                .title("Agentic URL Shortener API")
                .version("v1")
                .description("URL lifecycle, analytics, and governed SDLC orchestration APIs")
                .license(new License().name("MIT")));
    }
}

