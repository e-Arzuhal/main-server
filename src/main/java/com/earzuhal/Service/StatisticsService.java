package com.earzuhal.Service;

import com.earzuhal.dto.analysis.GraphRagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    private final WebClient webClient;

    public StatisticsService(@Qualifier("statisticsWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Analiz sonucunu statistics-server'a asenkron olarak kaydeder.
     * Fire-and-forget — hata durumunda ana akış kesilmez.
     *
     * @param contractType     Sözleşme türü (Türkçe, NLP'den gelen)
     * @param graphRagResult   GraphRAG analiz sonucu (matched_fields kaynağı)
     * @param completenessScore Tamamlanma skoru (0–100)
     */
    public void recordAsync(String contractType, GraphRagResponse graphRagResult, Double completenessScore) {
        List<String> features = extractFeatures(graphRagResult);

        Map<String, Object> body = new HashMap<>();
        body.put("contract_type", contractType);
        body.put("features", features);
        body.put("fields", Map.of());
        body.put("completeness_score", completenessScore != null ? completenessScore : 0.0);

        webClient.post()
                .uri("/contracts/analyze")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .subscribe(
                        response -> log.debug("İstatistik kaydedildi: tip={}, feature sayısı={}", contractType, features.size()),
                        error -> log.warn("Statistics servisi erişilemedi, analiz kaydı atlandı: {}", error.getMessage())
                );
    }

    private List<String> extractFeatures(GraphRagResponse graphRagResult) {
        if (graphRagResult == null || graphRagResult.getAnalysis() == null) {
            return List.of();
        }

        List<String> matchedFields = graphRagResult.getAnalysis().getMatchedFields();
        if (matchedFields == null) {
            return List.of();
        }

        return matchedFields.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
    }
}
