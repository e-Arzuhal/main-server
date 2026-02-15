package com.earzuhal.Service;

import com.earzuhal.dto.analysis.NlpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class NlpService {

    private static final Logger log = LoggerFactory.getLogger(NlpService.class);
    private final WebClient webClient;

    public NlpService(@Qualifier("nlpWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Metni NLP server'a gönderip analiz sonucunu döner.
     * POST /api/nlp/analyze  { "text": "..." }
     */
    public NlpResponse analyze(String text) {
        log.info("NLP analiz isteği gönderiliyor, metin uzunluğu: {}", text.length());

        try {
            return webClient.post()
                    .uri("/api/nlp/analyze")
                    .bodyValue(Map.of("text", text))
                    .retrieve()
                    .bodyToMono(NlpResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("NLP servis hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("NLP servisi yanıt vermedi: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("NLP servise bağlanılamadı: {}", e.getMessage());
            throw new RuntimeException("NLP servise bağlanılamadı. Servisin çalıştığından emin olun.", e);
        }
    }
}
