package com.earzuhal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /** Dahili API anahtarı — Python servisleri bu header ile doğrulama yapar */
    @Value("${services.internal-api-key:}")
    private String internalApiKey;

    private WebClient.Builder internalClientBuilder(String baseUrl) {
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        if (StringUtils.hasText(internalApiKey)) {
            builder.defaultHeader("X-Internal-API-Key", internalApiKey);
        }
        return builder;
    }

    @Bean
    public WebClient nlpWebClient(@Value("${services.nlp.base-url}") String baseUrl) {
        return internalClientBuilder(baseUrl).build();
    }

    @Bean
    public WebClient graphRagWebClient(@Value("${services.graphrag.base-url}") String baseUrl) {
        return internalClientBuilder(baseUrl).build();
    }

    @Bean
    public WebClient statisticsWebClient(@Value("${services.statistics.base-url}") String baseUrl) {
        return internalClientBuilder(baseUrl).build();
    }

    @Bean
    public WebClient chatbotWebClient(@Value("${services.chatbot.base-url}") String baseUrl) {
        return internalClientBuilder(baseUrl).build();
    }
}
