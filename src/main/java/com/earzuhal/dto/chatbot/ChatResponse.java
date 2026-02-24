package com.earzuhal.dto.chatbot;

import java.util.List;

public class ChatResponse {
    private String response;
    private List<String> suggestedQuestions;

    public ChatResponse() {}
    public ChatResponse(String response, List<String> suggestedQuestions) {
        this.response = response;
        this.suggestedQuestions = suggestedQuestions;
    }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public List<String> getSuggestedQuestions() { return suggestedQuestions; }
    public void setSuggestedQuestions(List<String> suggestedQuestions) { this.suggestedQuestions = suggestedQuestions; }
}
