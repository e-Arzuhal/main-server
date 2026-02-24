package com.earzuhal.Service;

import com.earzuhal.dto.chatbot.ChatRequest;
import com.earzuhal.dto.chatbot.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);
    private final WebClient webClient;

    public ChatbotService(@Qualifier("chatbotWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Kullanicidan gelen mesaji chatbot server'a iletir.
     * POST /api/chat { "message": "...", "history": [...] }
     */
    public ChatResponse chat(ChatRequest request) {
        log.info("Chatbot isteği gönderiliyor: {}", request.getMessage());

        try {
            return webClient.post()
                    .uri("/api/chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Chatbot servis hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Chatbot servisi yanıt vermedi: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Chatbot servise bağlanılamadı: {}", e.getMessage());
            throw new RuntimeException("Chatbot servise bağlanılamadı. Servisin çalıştığından emin olun.", e);
        }
    }
}
