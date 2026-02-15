package com.earzuhal.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FullAnalysisResponse {

    @JsonProperty("contract_type")
    private String contractType;

    @JsonProperty("contract_type_display")
    private String contractTypeDisplay;

    private double confidence;

    @JsonProperty("nlp_result")
    private NlpResponse nlpResult;

    @JsonProperty("graphrag_result")
    private GraphRagResponse graphRagResult;

    @JsonProperty("completeness_score")
    private Double completenessScore;
}
