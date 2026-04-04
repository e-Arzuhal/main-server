package com.earzuhal.Service;

import com.earzuhal.dto.analysis.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private final NlpService nlpService;
    private final GraphRagService graphRagService;
    private final StatisticsService statisticsService;

    public AnalysisService(NlpService nlpService, GraphRagService graphRagService, StatisticsService statisticsService) {
        this.nlpService = nlpService;
        this.graphRagService = graphRagService;
        this.statisticsService = statisticsService;
    }

    /**
     * Tam analiz akışı:
     * 1. Metin -> NLP Server (POST /api/v1/extract) -> contract_type + extracted_entities
     * 2. NLP sonucu -> GraphRAG Server (POST /api/v1/analyze/input) -> gap analysis + legal analysis
     * 3. İstatistik kaydı (fire-and-forget)
     * 4. Birleştirilmiş sonuç döner
     */
    public FullAnalysisResponse analyzeText(String text) {
        // Adım 1: NLP analizi
        log.info("Adım 1: NLP analizi başlıyor");
        NlpResponse nlpResult = nlpService.analyze(text);

        String turkishType = nlpResult.getContractType();
        String englishType = ContractTypeMapping.toEnglish(turkishType);

        log.info("NLP sonucu: tip={}, güven={}", turkishType, nlpResult.getContractTypeConfidence());

        // Adım 2: GraphRAG analizi
        log.info("Adım 2: GraphRAG analizi başlıyor");

        GraphRagResponse graphRagResult = null;
        Double completenessScore = null;
        Double complianceScore = null;

        try {
            graphRagResult = graphRagService.analyze(turkishType, nlpResult.getExtractedEntities());
            if (graphRagResult.getAnalysis() != null) {
                completenessScore = graphRagResult.getAnalysis().getCompletenessScore();
                complianceScore = graphRagResult.getAnalysis().getComplianceScore();
            }
            log.info("GraphRAG sonucu: tamamlanma={}%, uyumluluk={}%", completenessScore, complianceScore);
        } catch (Exception e) {
            log.warn("GraphRAG servisi erişilemedi, sadece NLP sonucu ile devam ediliyor: {}", e.getMessage());
        }

        // Adım 3: Statistics-server'a asenkron kaydet (fire-and-forget)
        statisticsService.recordAsync(turkishType, graphRagResult, completenessScore);

        // Adım 4: Birleştir ve dön
        return FullAnalysisResponse.builder()
                .contractType(englishType)
                .contractTypeDisplay(turkishType)
                .confidence(nlpResult.getContractTypeConfidence())
                .nlpResult(nlpResult)
                .graphRagResult(graphRagResult)
                .completenessScore(completenessScore)
                .complianceScore(complianceScore)
                .build();
    }
}
