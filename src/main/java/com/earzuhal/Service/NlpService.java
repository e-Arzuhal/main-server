package com.earzuhal.Service;

import com.earzuhal.dto.analysis.NlpResponse;
import com.earzuhal.dto.chatbot.ChatIntentResponse;
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
     * POST /api/v1/extract  { "text": "..." }
     */
    public NlpResponse analyze(String text) {
        log.info("NLP analiz isteği gönderiliyor, metin uzunluğu: {}", text.length());

        try {
            return webClient.post()
                    .uri("/api/v1/extract")
                    .bodyValue(Map.of("text", text))
                    .retrieve()
                    .bodyToMono(NlpResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("NLP servis hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("NLP servisi hata döndürdü: " + e.getStatusCode(), e);
        } catch (io.netty.channel.ConnectTimeoutException | java.util.concurrent.TimeoutException e) {
            log.error("NLP servis zaman aşımı (3s connect / 15s response): {}", e.getMessage());
            throw new RuntimeException("NLP servisi yanıt süresi aşıldı. Servisin çalıştığından emin olun.", e);
        } catch (Exception e) {
            log.error("NLP servise bağlanılamadı: {}", e.getMessage());
            throw new RuntimeException("NLP servise bağlanılamadı. Servisin çalıştığından emin olun.", e);
        }
    }

    /**
     * Chatbot mesajının niyetini sınıflandırır ve PII maskeleme yapar.
     * POST /api/v1/chat-intent { "message": "..." }
     */
    public ChatIntentResponse classifyIntent(String message) {
        log.info("Chat intent sınıflandırma isteği gönderiliyor");

        try {
            return webClient.post()
                    .uri("/api/v1/chat-intent")
                    .bodyValue(Map.of("message", message))
                    .retrieve()
                    .bodyToMono(ChatIntentResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("NLP chat-intent hatası: {} — fallback GENERAL_HELP", e.getStatusCode());
            return fallbackIntent(message);
        } catch (Exception e) {
            log.warn("NLP servise bağlanılamadı (chat-intent), fallback kullanılıyor: {}", e.getMessage());
            return fallbackIntent(message);
        }
    }

    /** NLP servisi erişilemezse GENERAL_HELP döner */
    private ChatIntentResponse fallbackIntent(String message) {
        ChatIntentResponse fallback = new ChatIntentResponse();
        fallback.setIntent("GENERAL_HELP");
        fallback.setConfidence(0.0);
        fallback.setSanitizedMessage(message);
        fallback.setDetectedEntities(Map.of());
        return fallback;
    }
}

