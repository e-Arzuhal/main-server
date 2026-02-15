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

    @JsonProperty("graph_data")
    private Map<String, Object> graphData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalysisResult {
        @JsonProperty("contract_type")
        private String contractType;

        @JsonProperty("matched_fields")
        private List<Map<String, Object>> matchedFields;

        @JsonProperty("missing_required")
        private List<Map<String, Object>> missingRequired;

        @JsonProperty("missing_recommended")
        private List<Map<String, Object>> missingRecommended;

        @JsonProperty("completeness_score")
        private double completenessScore;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Suggestions {
        private String status;
        private List<SuggestionItem> suggestions;

        @JsonProperty("next_action")
        private String nextAction;

        @JsonProperty("llm_prompt")
        private String llmPrompt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SuggestionItem {
        private String type;
        @JsonProperty("field_name")
        private String fieldName;
        private String message;
        private int priority;
        private String necessity;
    }
}
