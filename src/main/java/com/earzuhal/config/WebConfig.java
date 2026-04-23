package com.earzuhal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }


    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${app.env:development}")
    private String appEnv;

    @PostConstruct
    public void validateCorsConfiguration() {
        if ("production".equalsIgnoreCase(appEnv) && Arrays.stream(allowedOrigins)
                .anyMatch(origin -> StringUtils.hasText(origin) && "*".equals(origin.trim()))) {
            throw new IllegalStateException("In production, cors.allowed-origins cannot include '*'.");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
