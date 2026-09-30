package dev.assessment.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import dev.assessment.urlshortener.config.UrlShortenerProperties;
import dev.assessment.urlshortener.config.ReliabilityProperties;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        UrlShortenerProperties.class,
        ReliabilityProperties.class,
        AgenticExecutionProperties.class
})
public class AgenticUrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticUrlShortenerApplication.class, args);
    }
}
