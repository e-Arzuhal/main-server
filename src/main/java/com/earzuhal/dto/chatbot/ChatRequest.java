package com.earzuhal.dto.chatbot;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class ChatRequest {

    @NotBlank(message = "Mesaj boş olamaz")
    private String message;

    private List<ChatMessageDto> history;

    /**
     * Kullanıcının konuşmak istediği sözleşme. Belirtilmemişse en son
     * DRAFT/PENDING sözleşme otomatik seçilir; birden fazla varsa istemciye
     * seçim yaptırılır.
     */
    private Long contractId;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<ChatMessageDto> getHistory() { return history; }
    public void setHistory(List<ChatMessageDto> history) { this.history = history; }
    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }
}
