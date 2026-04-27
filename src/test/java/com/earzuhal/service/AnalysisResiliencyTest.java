package com.earzuhal.service;

import com.earzuhal.dto.analysis.FullAnalysisResponse;
import com.earzuhal.dto.analysis.GraphRagResponse;
import com.earzuhal.dto.analysis.NlpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Servis dayanıklılık testleri.
 *
 * GraphRAG veya Statistics servislerinin çökmesi ana pipeline'ı bloke etmemeli;
 * NLP hatası ise beklenen şekilde yayılmalı.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisResiliencyTest {

    @Mock NlpService nlpService;
    @Mock GraphRagService graphRagService;
    @Mock StatisticsService statisticsService;

    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(nlpService, graphRagService, statisticsService);
    }

    // ── GraphRAG dayanıklılığı ───────────────────────────────────────────────

    @Test
    @DisplayName("GraphRAG fallback — servis 'unavailable' dönünce analiz yine de tamamlanır")
    void graphrag_fallback_response_analysis_completes() {
        NlpResponse nlp = buildNlpResponse("kira_sozlesmesi", 0.85);
        GraphRagResponse fallback = buildFallbackGraphRag("kira_sozlesmesi");

        when(nlpService.analyze(anyString())).thenReturn(nlp);
        when(graphRagService.analyze(eq("kira_sozlesmesi"), anyMap())).thenReturn(fallback);
        doNothing().when(statisticsService).recordAsync(any(), any(), any());

        FullAnalysisResponse result = analysisService.analyzeText("Kira sözleşmesi metni");

        assertNotNull(result, "Fallback durumunda sonuç null olmamalı");
        assertNotNull(result.getGraphRagResult(), "Fallback GraphRAG yanıtı null olmamalı");
        assertEquals("unavailable", result.getGraphRagResult().getSuggestions().getStatus(),
                "Fallback status 'unavailable' olmalı");
        assertNotNull(result.getNlpResult(), "NLP sonucu her zaman dolu olmalı");
    }

    @Test
    @DisplayName("GraphRAG exception — beklenmeyen exception'da analiz graceful tamamlanır (null graphRagResult)")
    void graphrag_exception_analysis_completes_with_null_graphrag() {
        NlpResponse nlp = buildNlpResponse("is_sozlesmesi", 0.72);

        when(nlpService.analyze(anyString())).thenReturn(nlp);
        when(graphRagService.analyze(anyString(), anyMap()))
                .thenThrow(new RuntimeException("Bağlantı hatası"));
        doNothing().when(statisticsService).recordAsync(any(), any(), any());

        FullAnalysisResponse result = assertDoesNotThrow(
                () -> analysisService.analyzeText("İş sözleşmesi"),
                "GraphRAG exception pipeline'ı durdurmamalı"
        );

        assertNotNull(result);
        assertNull(result.getGraphRagResult(), "Exception sonrası graphRagResult null olmalı");
        assertNotNull(result.getNlpResult());
    }

    // ── NLP dayanıklılığı ────────────────────────────────────────────────────

    @Test
    @DisplayName("NLP exception — NLP hatası RuntimeException olarak yayılır (pipeline durur)")
    void nlp_exception_propagates_to_caller() {
        when(nlpService.analyze(anyString()))
                .thenThrow(new RuntimeException("NLP servisi yanıt süresi aşıldı"));

        assertThrows(
                RuntimeException.class,
                () -> analysisService.analyzeText("Herhangi bir metin"),
                "NLP hatası caller'a yayılmalı"
        );

        verifyNoInteractions(graphRagService, statisticsService);
    }

    // ── Statistics fire-and-forget dayanıklılığı ────────────────────────────

    @Test
    @DisplayName("Statistics fire-and-forget — Statistics hatası ana analizi kesmez")
    void statistics_failure_does_not_crash_pipeline() {
        NlpResponse nlp = buildNlpResponse("hizmet_sozlesmesi", 0.60);
        GraphRagResponse graphRag = buildFallbackGraphRag("hizmet_sozlesmesi");

        when(nlpService.analyze(anyString())).thenReturn(nlp);
        when(graphRagService.analyze(anyString(), anyMap())).thenReturn(graphRag);
        // Statistics kaydı sessizce hata verir (fire-and-forget)
        doThrow(new RuntimeException("Statistics servisi bağlanamıyor"))
                .when(statisticsService).recordAsync(any(), any(), any());

        FullAnalysisResponse result = assertDoesNotThrow(
                () -> analysisService.analyzeText("Hizmet sözleşmesi"),
                "Statistics hatası pipeline'ı durdurmamalı"
        );

        assertNotNull(result);
        assertNotNull(result.getNlpResult());
    }

    @Test
    @DisplayName("Statistics çağrılır — başarılı akışta recordAsync mutlaka bir kez çağrılır")
    void statistics_record_async_called_once_on_success() {
        NlpResponse nlp = buildNlpResponse("kira_sozlesmesi", 0.90);
        GraphRagResponse graphRag = buildFallbackGraphRag("kira_sozlesmesi");

        when(nlpService.analyze(anyString())).thenReturn(nlp);
        when(graphRagService.analyze(anyString(), anyMap())).thenReturn(graphRag);
        doNothing().when(statisticsService).recordAsync(any(), any(), any());

        analysisService.analyzeText("Kira sözleşmesi");

        verify(statisticsService, times(1)).recordAsync(eq("kira_sozlesmesi"), any(), any());
    }

    // ── Yardımcı metodlar ────────────────────────────────────────────────────

    private NlpResponse buildNlpResponse(String contractType, double confidence) {
        NlpResponse nlp = new NlpResponse();
        nlp.setContractType(contractType);
        nlp.setContractTypeConfidence(confidence);
        nlp.setExtractedEntities(Map.of(
                "PERSON", List.of(), "ORG", List.of(), "LOC", List.of(),
                "MONEY", List.of(), "DATE", List.of(), "CARDINAL", List.of(), "PERCENT", List.of()
        ));
        nlp.setRawTextLength(50);
        return nlp;
    }

    private GraphRagResponse buildFallbackGraphRag(String contractType) {
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
        suggestions.setNextAction("GraphRAG servisi şu an erişilemiyor.");
        suggestions.setChatbotQuestions(List.of());
        resp.setSuggestions(suggestions);

        return resp;
    }
}
