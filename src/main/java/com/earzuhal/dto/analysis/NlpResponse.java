package com.earzuhal.dto.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NlpResponse {

    private boolean success;

    @JsonProperty("contract_type")
    private String contractType;

    private double confidence;

    private List<EntityItem> entities;

    @JsonProperty("extracted_fields")
    private ExtractedFields extractedFields;

    private List<String> suggestions;

    @JsonProperty("raw_text")
    private String rawText;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EntityItem {
        private String text;
        private String label;
        private int start;
        private int end;
        @JsonProperty("mapped_field")
        private String mappedField;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedFields {
        private List<String> taraflar;
        private String tutar;
        private String tarih;
        private String lokasyon;
        private String kurum;
    }

    /**
     * NLP entity'lerini GraphRAG formatına dönüştürür.
     * GraphRAG beklediği format: {"PERSON": [...], "MONEY": [...], ...}
     */
    public Map<String, List<String>> toGraphRagEntities() {
        var map = new java.util.HashMap<String, List<String>>();
        if (entities == null) return map;

        for (EntityItem e : entities) {
            map.computeIfAbsent(e.getLabel(), k -> new java.util.ArrayList<>()).add(e.getText());
        }
        return map;
    }
}
