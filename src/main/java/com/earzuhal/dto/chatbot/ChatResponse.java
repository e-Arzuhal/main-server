package com.earzuhal.dto.chatbot;

import java.util.List;

public class ChatResponse {
    private String response;
    private List<String> suggestedQuestions;

    /**
     * Doğru olduğunda istemci kullanıcıya sözleşme seçimi yaptırmalı ve aynı
     * mesajı {@link ChatRequest#getContractId()} doldurarak yeniden göndermeli.
     */
    private Boolean requiresContractSelection;

    /** Seçim için kullanıcıya gösterilecek sözleşme listesi (id + label). */
    private List<ContractOption> contractOptions;

    public ChatResponse() {}
    public ChatResponse(String response, List<String> suggestedQuestions) {
        this.response = response;
        this.suggestedQuestions = suggestedQuestions;
    }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public List<String> getSuggestedQuestions() { return suggestedQuestions; }
    public void setSuggestedQuestions(List<String> suggestedQuestions) { this.suggestedQuestions = suggestedQuestions; }
    public Boolean getRequiresContractSelection() { return requiresContractSelection; }
    public void setRequiresContractSelection(Boolean requiresContractSelection) { this.requiresContractSelection = requiresContractSelection; }
    public List<ContractOption> getContractOptions() { return contractOptions; }
    public void setContractOptions(List<ContractOption> contractOptions) { this.contractOptions = contractOptions; }

    public static class ContractOption {
        private Long id;
        private String title;
        private String type;
        private String status;

        public ContractOption() {}
        public ContractOption(Long id, String title, String type, String status) {
            this.id = id; this.title = title; this.type = type; this.status = status;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
