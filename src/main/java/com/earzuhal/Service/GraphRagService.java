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
            throw new RuntimeException("GraphRAG servisi yanıt vermedi: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("GraphRAG servise bağlanılamadı: {}", e.getMessage());
            throw new RuntimeException("GraphRAG servise bağlanılamadı. Servisin çalıştığından emin olun.", e);
        }
    }
}
