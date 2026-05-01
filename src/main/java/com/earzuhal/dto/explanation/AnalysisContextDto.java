package com.earzuhal.dto.explanation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Analiz sonucundan (FullAnalysisResponse) frontend'in sözleşme oluştururken
 * geri gönderdiği özet bilgi. Bu DTO, ExplanationService'in madde açıklamaları
 * üretmesi için gereken tüm veriyi taşır.
 *
 * Frontend'de FullAnalysisResponse döndükten sonra ContractRequest.analysisContext
 * alanına şu alanlar doldurularak gönderilir:
 *   contractType        → fullAnalysisResponse.contractType         (İngilizce)
 *   contractTypeDisplay → fullAnalysisResponse.contractTypeDisplay  (Türkçe)
 *   confidence          → fullAnalysisResponse.confidence
 *   completenessScore   → fullAnalysisResponse.completenessScore
 *   suggestions         → fullAnalysisResponse.graphRagResult.suggestions
 *   missingRequired     → fullAnalysisResponse.graphRagResult.analysis.missingRequired
 *   entities            → fullAnalysisResponse.nlpResult.entities
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisContextDto {

    /** İngilizce sözleşme tipi (ör. "RENTAL", "SALES") */
    private String contractType;

    /** Türkçe orijinal sözleşme tipi (ör. "kira_sozlesmesi") */
    private String contractTypeDisplay;

    /** NLP sınıflandırma güveni (0.0–1.0) */
    private Double confidence;

    /** GraphRAG tamamlanma skoru (0–100) */
    private Double completenessScore;

    /**
     * GraphRAG önerileri. Her eleman:
     * { fieldName, message, necessity ("required"|"recommended"), priority }
     */
    private List<Map<String, Object>> suggestions;

    /** GraphRAG'ın zorunlu olarak bildirdiği eksik alanlar */
    private List<String> missingRequired;

    /**
     * NLP entity'leri. Her eleman:
     * { text, label ("PERSON"|"MONEY"|"DATE"|...), mappedField }
     */
    private List<Map<String, Object>> entities;

    /**
     * Gemini'nin eksik zorunlu alanlar için ürettiği hukuki risk listesi.
     * Her eleman: { field, riskLevel, tbkArticle, explanation, suggestion }.
     * Sözleşme oluşturulduğunda persiste edilir; daha sonra frontend
     * "Bulunması Gereken Maddeler" kartında gerekçe + TBK madde olarak gösterir.
     */
    private List<Map<String, Object>> risks;

    /**
     * Gemini servisi gerçekten kullanılamadığında (503 fallback) frontend'in
     * "AI servisi şu an erişilemiyor" rozetini gösterebilmesi için flag.
     */
    private Boolean fallbackUsed;
}
