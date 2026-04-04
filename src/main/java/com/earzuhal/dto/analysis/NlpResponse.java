package com.earzuhal.dto.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NlpResponse {

    @JsonProperty("contract_type")
    private String contractType;

    @JsonProperty("contract_type_confidence")
    private double contractTypeConfidence;

    @JsonProperty("extracted_entities")
    private Map<String, List<String>> extractedEntities;

    @JsonProperty("raw_text_length")
    private int rawTextLength;

    @JsonProperty("processing_time_ms")
    private int processingTimeMs;
}
