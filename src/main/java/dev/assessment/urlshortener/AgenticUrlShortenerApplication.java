package dev.assessment.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import dev.assessment.urlshortener.config.UrlShortenerProperties;

@SpringBootApplication
@EnableConfigurationProperties(UrlShortenerProperties.class)
public class AgenticUrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticUrlShortenerApplication.class, args);
    }
}
