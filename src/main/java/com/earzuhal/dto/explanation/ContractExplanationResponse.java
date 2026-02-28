package com.earzuhal.dto.explanation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/contracts/{id}/explanation endpoint'inin döndürdüğü tam açıklama yanıtı.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractExplanationResponse {

    private Long contractId;
    private String contractTitle;
    private String contractType;

    /** Sözleşmedeki her madde için açıklama listesi */
    private List<ClauseExplanationItem> clauses;

    /** NLP'den gelen genel sınıflandırma güveni */
    private Double overallConfidence;

    /** GraphRAG tamamlanma skoru */
    private Double completenessScore;

    /** Eksik zorunlu maddeler */
    private List<String> missingRequired;

    /** Açıklamanın oluşturulduğu zaman */
    private OffsetDateTime generatedAt;
}
