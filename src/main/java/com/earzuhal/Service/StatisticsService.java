package com.earzuhal.Service;

import com.earzuhal.dto.analysis.GraphRagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    private final WebClient webClient;

    @Value("${services.statistics.retry.max-attempts:2}")
    private long retryMaxAttempts;

    @Value("${services.statistics.retry.backoff-ms:300}")
    private long retryBackoffMs;

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
        List<Map<String, Object>> clauseData = buildClauseData(graphRagResult);
        int optionalOffered = extractOptionalOfferedCount(graphRagResult);

        Map<String, Object> body = new HashMap<>();
        body.put("contract_type", contractType);
        body.put("features", features);
        body.put("fields", Map.of());
        body.put("completeness_score", completenessScore != null ? completenessScore : 0.0);
        body.put("clause_data", clauseData);
        body.put("optional_clauses_offered", optionalOffered);
        body.put("optional_clauses_selected", 0);

        webClient.post()
                .uri("/contracts/analyze")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(
                        Retry.backoff(retryMaxAttempts, Duration.ofMillis(retryBackoffMs))
                                .filter(this::isRetryable)
                )
                .subscribe(
                        response -> log.debug("İstatistik kaydedildi: tip={}, feature sayısı={}", contractType, features.size()),
                        error -> log.warn("Statistics servisi erişilemedi, analiz kaydı atlandı: {}", error.getMessage())
                );
    }

    /**
     * Sözleşme onay/ret sonucunu statistics-server'a asenkron olarak bildirir.
     * Fire-and-forget — hata durumunda ana akış kesilmez.
     *
     * @param contractType  Türkçe sözleşme tipi (ContractTypeMapping.toTurkish ile dönüştürülmüş)
     * @param approved      true → onaylandı, false → reddedildi
     */
    public void recordOutcomeAsync(String contractType, boolean approved) {
        webClient.post()
                .uri("/stats/{type}/mark-outcome", contractType)
                .bodyValue(java.util.Map.of("approved", approved))
                .retrieve()
                .bodyToMono(Void.class)
                .subscribe(
                        ignored -> log.debug("Sonuç istatistiği güncellendi: tip={}, onaylı={}", contractType, approved),
                        error  -> log.warn("Statistics sonuç güncellemesi başarısız: {}", error.getMessage())
                );
    }

    private boolean isRetryable(Throwable error) {
        if (error instanceof WebClientResponseException webClientError) {
            return webClientError.getStatusCode().is5xxServerError() || webClientError.getStatusCode().value() == 429;
        }
        return true;
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

    private List<Map<String, Object>> buildClauseData(GraphRagResponse graphRagResult) {
        if (graphRagResult == null || graphRagResult.getAnalysis() == null ||
                graphRagResult.getAnalysis().getMatchedFields() == null) {
            return List.of();
        }

        return graphRagResult.getAnalysis().getMatchedFields().stream()
                .filter(field -> field != null && !field.isBlank())
                .map(field -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("clause", field);
                    item.put("reason", "GraphRAG analysis matched this clause");
                    item.put("law_reference", null);
                    item.put("necessity", "required");
                    return item;
                })
                .collect(Collectors.toList());
    }

    private int extractOptionalOfferedCount(GraphRagResponse graphRagResult) {
        if (graphRagResult == null || graphRagResult.getAnalysis() == null ||
                graphRagResult.getAnalysis().getMissingOptional() == null) {
            return 0;
        }
        return (int) graphRagResult.getAnalysis().getMissingOptional().stream()
                .filter(field -> field != null && !field.isBlank())
                .count();
    }
}
