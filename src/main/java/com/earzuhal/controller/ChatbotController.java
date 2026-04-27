package com.earzuhal.controller;

import com.earzuhal.service.ChatbotService;
import com.earzuhal.dto.chatbot.ChatRequest;
import com.earzuhal.dto.chatbot.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    /**
     * Chatbot mesajını işler ve yanıt döner.
     * POST /api/chat
     * { "message": "Sözleşme nasıl oluştururum?", "history": [...] }
     *
     * Orkestrasyon: main-server → NLP (intent+PII) → GraphRAG (koşullu) → chatbot-server (Gemini)
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        ChatResponse response = chatbotService.chat(request, username);
        return ResponseEntity.ok(response);
    }
}
