package com.earzuhal.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Main-server'dan chatbot-server'a gönderilen zenginleştirilmiş istek.
 * Orijinal mesaj + NLP intent + sözleşme bağlamı + GraphRAG bilgisi içerir.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedChatRequest {

    /** PII temizlenmiş mesaj (NLP-server'dan) */
    @JsonProperty("sanitized_message")
    private String sanitizedMessage;

    /** Orijinal mesaj (PII temizlenmemiş — chatbot açısından fallback) */
    private String message;

    /** NLP intent türü */
    @JsonProperty("intent")
    private String intent;

    /** Sözleşme bağlam özeti (tip, taraflar, maddeler vs.) — snake_case Python tarafı için */
    @JsonProperty("contract_context")
    private String contractContext;

    /** GraphRAG'den gelen analiz / kanun bilgisi — snake_case Python tarafı için */
    @JsonProperty("graphrag_context")
    private String graphRagContext;

    /** Konuşma geçmişi */
    private List<ChatMessageDto> history;
}
