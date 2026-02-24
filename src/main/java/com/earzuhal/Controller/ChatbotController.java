package com.earzuhal.Controller;

import com.earzuhal.Service.ChatbotService;
import com.earzuhal.dto.chatbot.ChatRequest;
import com.earzuhal.dto.chatbot.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatbotService.chat(request);
        return ResponseEntity.ok(response);
    }
}
