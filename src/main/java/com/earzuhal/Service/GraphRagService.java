package com.earzuhal.Service;

import com.earzuhal.dto.analysis.GraphRagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class GraphRagService {

    private static final Logger log = LoggerFactory.getLogger(GraphRagService.class);
    private final WebClient webClient;

    public GraphRagService(@Qualifier("graphRagWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * NLP'den çıkan entity'leri GraphRAG'e gönderip analiz sonucunu döner.
     * POST /api/v1/analyze/input
     * {
     *   "contract_type": "kira_sozlesmesi",
     *   "extracted_entities": { "PERSON": [...], "MONEY": [...] }
     * }
     */
    public GraphRagResponse analyze(String contractType, Map<String, List<String>> extractedEntities) {
        log.info("GraphRAG analiz isteği: tip={}, entity sayısı={}", contractType, extractedEntities.size());

        Map<String, Object> body = Map.of(
                "contract_type", contractType,
                "extracted_entities", extractedEntities
        );

        try {
            return webClient.post()
                    .uri("/api/v1/analyze/input")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(GraphRagResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("GraphRAG servis hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            log.warn("GraphRAG servis hatası nedeniyle boş analiz dönülüyor");
            return buildFallbackResponse(contractType);
        } catch (io.netty.channel.ConnectTimeoutException | java.util.concurrent.TimeoutException e) {
            log.error("GraphRAG servis zaman aşımı: {}", e.getMessage());
            return buildFallbackResponse(contractType);
        } catch (Exception e) {
            log.error("GraphRAG servise bağlanılamadı: {}", e.getMessage());
            return buildFallbackResponse(contractType);
        }
    }

    /** GraphRAG erişilemezse analiz adımını atlatmak için minimal geçerli yanıt. */
    private GraphRagResponse buildFallbackResponse(String contractType) {
        GraphRagResponse resp = new GraphRagResponse();

        GraphRagResponse.AnalysisResult analysis = new GraphRagResponse.AnalysisResult();
        analysis.setContractType(contractType);
        analysis.setCompletenessScore(0.0);
        analysis.setMissingRequired(List.of());
        analysis.setMissingOptional(List.of());
        analysis.setMatchedFields(List.of());
        analysis.setValidationErrors(List.of());
        resp.setAnalysis(analysis);

        GraphRagResponse.Suggestions suggestions = new GraphRagResponse.Suggestions();
        suggestions.setStatus("unavailable");
        suggestions.setNextAction("GraphRAG servisi şu an erişilemiyor. Analiz sonuçları mevcut değil.");
        suggestions.setChatbotQuestions(List.of());
        resp.setSuggestions(suggestions);

        return resp;
    }
}
