package com.earzuhal.config;

import com.earzuhal.security.RequestIdFilter;
import io.netty.channel.ChannelOption;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /** Dahili API anahtarı — Python servisleri bu header ile doğrulama yapar */
    @Value("${services.internal-api-key:}")
    private String internalApiKey;

    @Value("${app.env:development}")
    private String appEnv;

    @PostConstruct
    public void validateInternalApiKey() {
        if ("production".equalsIgnoreCase(appEnv) && !StringUtils.hasText(internalApiKey)) {
            throw new IllegalStateException("services.internal-api-key must be set in production.");
        }
    }

    /**
     * Propagates X-Request-ID from MDC to outbound calls so all microservice
     * logs for the same user request share the same correlation id.
     */
    private ExchangeFilterFunction requestIdPropagator() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                return reactor.core.publisher.Mono.just(
                    ClientRequest.from(clientRequest)
                        .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                        .build()
                );
            }
            return reactor.core.publisher.Mono.just(clientRequest);
        });
    }

    private WebClient.Builder internalClientBuilder(String baseUrl, Duration responseTimeout) {
        // 3 s bağlantı; yanıt zaman aşımı servise göre değişir (Ollama tabanlı NLP daha uzun sürer)
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
                .responseTimeout(responseTimeout);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(requestIdPropagator());
        if (StringUtils.hasText(internalApiKey)) {
            builder.defaultHeader("X-Internal-API-Key", internalApiKey);
        }
        return builder;
    }

    @Bean
    public WebClient nlpWebClient(
            @Value("${services.nlp.base-url}") String baseUrl,
            @Value("${services.nlp.response-timeout-seconds:120}") long responseTimeoutSeconds) {
        return internalClientBuilder(baseUrl, Duration.ofSeconds(responseTimeoutSeconds)).build();
    }

    @Bean
    public WebClient graphRagWebClient(
            @Value("${services.graphrag.base-url}") String baseUrl,
            @Value("${services.graphrag.response-timeout-seconds:120}") long responseTimeoutSeconds) {
        return internalClientBuilder(baseUrl, Duration.ofSeconds(responseTimeoutSeconds)).build();
    }

    @Bean
    public WebClient statisticsWebClient(@Value("${services.statistics.base-url}") String baseUrl) {
        return internalClientBuilder(baseUrl, Duration.ofSeconds(15)).build();
    }

    @Bean
    public WebClient chatbotWebClient(@Value("${services.chatbot.base-url}") String baseUrl) {
        return internalClientBuilder(baseUrl, Duration.ofSeconds(15)).build();
    }
}
