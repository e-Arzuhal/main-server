package com.earzuhal.dto.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphRagResponse {

    private AnalysisResult analysis;
    private Suggestions suggestions;

    @JsonProperty("legal_analysis")
    private LegalAnalysis legalAnalysis;

    @JsonProperty("graph_data")
    private Map<String, Object> graphData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalysisResult {
        @JsonProperty("contract_type")
        private String contractType;

        @JsonProperty("completeness_score")
        private double completenessScore;

        @JsonProperty("compliance_score")
        private Double complianceScore;

        @JsonProperty("needs_llm_analysis")
        private boolean needsLlmAnalysis;

        @JsonProperty("matched_fields")
        private List<String> matchedFields;

        @JsonProperty("missing_required")
        private List<String> missingRequired;

        @JsonProperty("missing_optional")
        private List<String> missingOptional;

        @JsonProperty("validation_errors")
        private List<String> validationErrors;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Suggestions {
        private String status;

        @JsonProperty("next_action")
        private String nextAction;

        @JsonProperty("chatbot_questions")
        private List<ChatbotQuestion> chatbotQuestions;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatbotQuestion {
        private int priority;
        private String field;
        private String question;
        private boolean required;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LegalAnalysis {
        @JsonProperty("tbk_articles")
        private List<Integer> tbkArticles;

        private List<Risk> risks;

        @JsonProperty("general_assessment")
        private String generalAssessment;

        @JsonProperty("compliance_penalty")
        private Double compliancePenalty;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Risk {
        private String field;

        @JsonProperty("risk_level")
        private String riskLevel;

        @JsonProperty("tbk_article")
        private Integer tbkArticle;

        private String explanation;
        private String suggestion;
    }
}
