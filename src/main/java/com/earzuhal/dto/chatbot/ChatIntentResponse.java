package com.earzuhal.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * NLP-server /api/v1/chat-intent yanıt DTO'su.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatIntentResponse {

    private String intent;
    private Double confidence;

    @JsonProperty("sanitized_message")
    private String sanitizedMessage;

    @JsonProperty("detected_entities")
    private Map<String, List<String>> detectedEntities;
}
