package com.earzuhal.service;

import com.earzuhal.dto.analysis.NlpResponse;
import com.earzuhal.dto.chatbot.ChatIntentResponse;
import com.earzuhal.exception.SanitizationUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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
        } catch (WebClientRequestException e) {
            log.error("NLP servis bağlantı/zaman aşımı: {}", e.getMessage());
            throw new RuntimeException("NLP servisi yanıt süresi aşıldı. Servisin çalıştığından emin olun.", e);
        } catch (Exception e) {
            log.error("NLP servise bağlanılamadı: {}", e.getMessage());
            throw new RuntimeException("NLP servise bağlanılamadı. Servisin çalıştığından emin olun.", e);
        }
    }

    /**
     * Chatbot mesajının niyetini sınıflandırır ve PII maskeleme yapar.
     * POST /api/v1/chat-intent { "message": "..." }
     *
     * NLP servisi erişilemezse fallback YAPILMAZ — ham (PII içerebilen)
     * kullanıcı metninin Gemini gibi dış LLM servislerine sızmasını
     * engellemek için {@link SanitizationUnavailableException} fırlatılır.
     */
    public ChatIntentResponse classifyIntent(String message) {
        log.info("Chat intent sınıflandırma isteği gönderiliyor");

        ChatIntentResponse response;
        try {
            response = webClient.post()
                    .uri("/api/v1/chat-intent")
                    .bodyValue(Map.of("message", message))
                    .retrieve()
                    .bodyToMono(ChatIntentResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("NLP chat-intent hata kodu: {} - sanitization yapılamadı, istek reddediliyor",
                    e.getStatusCode());
            throw new SanitizationUnavailableException(
                    "NLP sanitization servisi geçici olarak hata döndürüyor: " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.error("NLP servise bağlanılamadı (chat-intent) — sanitization yapılamadı, istek reddediliyor: {}",
                    e.getMessage());
            throw new SanitizationUnavailableException(
                    "NLP sanitization servisine bağlanılamadı.", e);
        } catch (Exception e) {
            log.error("NLP chat-intent beklenmeyen hata — sanitization yapılamadı, istek reddediliyor: {}",
                    e.getMessage());
            throw new SanitizationUnavailableException(
                    "NLP sanitization sırasında beklenmeyen hata oluştu.", e);
        }

        // Yanıt geldi ama sanitized_message alanı boşsa, ham mesajın LLM'e gitmesine
        // izin verme — yine güvenlik açığı sayılır.
        if (response == null || response.getSanitizedMessage() == null) {
            log.error("NLP chat-intent yanıtında sanitized_message alanı yok — istek reddediliyor");
            throw new SanitizationUnavailableException(
                    "NLP sanitization servisi geçersiz yanıt döndürdü.", null);
        }
        return response;
    }
}

