package com.earzuhal.Service;

import com.earzuhal.dto.analysis.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private final NlpService nlpService;
    private final GraphRagService graphRagService;

    public AnalysisService(NlpService nlpService, GraphRagService graphRagService) {
        this.nlpService = nlpService;
        this.graphRagService = graphRagService;
    }

    /**
     * Tam analiz akışı:
     * 1. Metin -> NLP Server (sözleşme tipi + entity extraction)
     * 2. NLP sonucu -> GraphRAG Server (eksik alan analizi + öneriler)
     * 3. Birleştirilmiş sonuç döner
     */
    public FullAnalysisResponse analyzeText(String text) {
        // Adım 1: NLP analizi
        log.info("Adım 1: NLP analizi başlıyor");
        NlpResponse nlpResult = nlpService.analyze(text);

        if (!nlpResult.isSuccess()) {
            throw new RuntimeException("NLP analizi başarısız oldu.");
        }

        String turkishType = nlpResult.getContractType();
        String englishType = ContractTypeMapping.toEnglish(turkishType);

        log.info("NLP sonucu: tip={}, güven={}", turkishType, nlpResult.getConfidence());

        // Adım 2: GraphRAG analizi
        log.info("Adım 2: GraphRAG analizi başlıyor");
        Map<String, List<String>> graphRagEntities = nlpResult.toGraphRagEntities();

        GraphRagResponse graphRagResult = null;
        Double completenessScore = null;

        try {
            graphRagResult = graphRagService.analyze(turkishType, graphRagEntities);
            if (graphRagResult.getAnalysis() != null) {
                completenessScore = graphRagResult.getAnalysis().getCompletenessScore();
            }
            log.info("GraphRAG sonucu: tamamlanma={}%", completenessScore);
        } catch (Exception e) {
            log.warn("GraphRAG servisi erişilemedi, sadece NLP sonucu ile devam ediliyor: {}", e.getMessage());
        }

        // Adım 3: Birleştir ve dön
        return FullAnalysisResponse.builder()
                .contractType(englishType)
                .contractTypeDisplay(turkishType)
                .confidence(nlpResult.getConfidence())
                .nlpResult(nlpResult)
                .graphRagResult(graphRagResult)
                .completenessScore(completenessScore)
                .build();
    }
}
