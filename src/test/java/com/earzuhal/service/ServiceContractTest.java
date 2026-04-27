package com.earzuhal.service;

import com.earzuhal.dto.analysis.GraphRagResponse;
import com.earzuhal.dto.analysis.NlpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Servisler arası kontrat testleri.
 *
 * Amaç: Python mikroservislerin döndüğü JSON yapısının main-server DTO'larıyla
 * uyumlu olduğunu doğrulamak. Bir serviste alan adı değişirse bu testler yakalanır.
 *
 * Her test "Python servis şu JSON'u gönderir → Java DTO doğru parse eder" modelini izler.
 */
class ServiceContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ── NLP Servis Kontratı ──────────────────────────────────────────────────

    @Test
    @DisplayName("NLP kontrat: contract_type alanı doğru map ediliyor")
    void nlp_contract_type_field_maps_correctly() throws Exception {
        String nlpJson = """
            {
              "contract_type": "kira_sozlesmesi",
              "contract_type_confidence": 0.87,
              "extracted_entities": {
                "PERSON": ["Ahmet Yılmaz", "Fatma Demir"],
                "ORG": [],
                "LOC": ["İstanbul"],
                "MONEY": ["15.000 TL"],
                "DATE": ["01.03.2025"],
                "CARDINAL": ["1 yıl"],
                "PERCENT": []
              },
              "raw_text_length": 250,
              "processing_time_ms": 340
            }
            """;

        NlpResponse response = mapper.readValue(nlpJson, NlpResponse.class);

        assertEquals("kira_sozlesmesi", response.getContractType(),
                "contract_type alanı @JsonProperty ile doğru eşlenmeli");
        assertEquals(0.87, response.getContractTypeConfidence(), 0.001,
                "contract_type_confidence alanı doğru parse edilmeli");
        assertNotNull(response.getExtractedEntities(), "extracted_entities null olmamalı");
        assertTrue(response.getExtractedEntities().containsKey("PERSON"),
                "PERSON entity tipi map'te olmalı");
        assertEquals(List.of("Ahmet Yılmaz", "Fatma Demir"),
                response.getExtractedEntities().get("PERSON"));
        assertEquals(250, response.getRawTextLength());
    }

    @Test
    @DisplayName("NLP kontrat: tüm zorunlu entity tipleri kabul ediliyor")
    void nlp_contract_all_entity_types_accepted() throws Exception {
        String nlpJson = """
            {
              "contract_type": "is_sozlesmesi",
              "contract_type_confidence": 0.65,
              "extracted_entities": {
                "PERSON": ["Ali Veli"],
                "ORG": ["ABC A.Ş."],
                "LOC": [],
                "MONEY": ["25.000 TL"],
                "DATE": ["01.01.2025"],
                "CARDINAL": ["2 ay"],
                "PERCENT": ["%10"]
              },
              "raw_text_length": 180,
              "processing_time_ms": 290
            }
            """;

        NlpResponse response = mapper.readValue(nlpJson, NlpResponse.class);

        for (String key : List.of("PERSON", "ORG", "LOC", "MONEY", "DATE", "CARDINAL", "PERCENT")) {
            assertTrue(response.getExtractedEntities().containsKey(key),
                    "Entity tipi eksik: " + key);
        }
    }

    @Test
    @DisplayName("NLP kontrat: bilinmeyen alanlar @JsonIgnoreProperties ile sessizce görmezden geliniyor")
    void nlp_contract_unknown_fields_ignored() throws Exception {
        String nlpJsonWithExtra = """
            {
              "contract_type": "hizmet_sozlesmesi",
              "contract_type_confidence": 0.55,
              "extracted_entities": {},
              "raw_text_length": 100,
              "processing_time_ms": 50,
              "future_field_v2": "ignored_value",
              "debug_info": {"model": "qwen2.5"}
            }
            """;

        assertDoesNotThrow(
                () -> mapper.readValue(nlpJsonWithExtra, NlpResponse.class),
                "@JsonIgnoreProperties(ignoreUnknown=true) bilinmeyen alanlarda exception fırlatmamalı"
        );
    }

    // ── GraphRAG Servis Kontratı ─────────────────────────────────────────────

    @Test
    @DisplayName("GraphRAG kontrat: analysis bloğu doğru map ediliyor")
    void graphrag_contract_analysis_block_maps_correctly() throws Exception {
        String graphRagJson = """
            {
              "analysis": {
                "contract_type": "is_sozlesmesi",
                "completeness_score": 67.0,
                "compliance_score": 52.0,
                "needs_llm_analysis": true,
                "matched_fields": ["taraf_isci", "ucret"],
                "missing_required": ["is_tanimi", "calisma_yeri"],
                "missing_optional": ["sure"],
                "validation_errors": []
              },
              "suggestions": {
                "status": "incomplete",
                "next_action": "İş tanımı nedir?",
                "chatbot_questions": [
                  {"priority": 1, "field": "is_tanimi", "question": "Pozisyon nedir?", "required": true}
                ]
              },
              "legal_analysis": {
                "tbk_articles": [393, 419],
                "risks": [
                  {
                    "field": "is_tanimi",
                    "risk_level": "HIGH",
                    "tbk_article": 393,
                    "explanation": "İş tanımı eksik.",
                    "suggestion": "Ekleyin."
                  }
                ],
                "general_assessment": "Eksik alanlar var.",
                "compliance_penalty": 0.15
              },
              "graph_data": {}
            }
            """;

        GraphRagResponse response = mapper.readValue(graphRagJson, GraphRagResponse.class);

        assertNotNull(response.getAnalysis(), "analysis bloğu null olmamalı");
        assertEquals("is_sozlesmesi", response.getAnalysis().getContractType());
        assertEquals(67.0, response.getAnalysis().getCompletenessScore(), 0.01);
        assertTrue(response.getAnalysis().isNeedsLlmAnalysis());
        assertFalse(response.getAnalysis().getMissingRequired().isEmpty(),
                "missing_required listesi dolu olmalı");

        assertNotNull(response.getSuggestions(), "suggestions bloğu null olmamalı");
        assertEquals("incomplete", response.getSuggestions().getStatus());
        assertEquals(1, response.getSuggestions().getChatbotQuestions().size());

        assertNotNull(response.getLegalAnalysis(), "legal_analysis bloğu null olmamalı");
        assertTrue(response.getLegalAnalysis().getTbkArticles().contains(393));
        assertEquals(1, response.getLegalAnalysis().getRisks().size());
        assertEquals("HIGH", response.getLegalAnalysis().getRisks().get(0).getRiskLevel());
    }

    @Test
    @DisplayName("GraphRAG kontrat: legal_analysis null olabilir (complete sözleşmede LLM çağrılmaz)")
    void graphrag_contract_null_legal_analysis_accepted() throws Exception {
        String graphRagJson = """
            {
              "analysis": {
                "contract_type": "taahhutname",
                "completeness_score": 100.0,
                "needs_llm_analysis": false,
                "matched_fields": ["taraf_1", "taraf_2"],
                "missing_required": [],
                "missing_optional": [],
                "validation_errors": []
              },
              "suggestions": {
                "status": "complete",
                "next_action": null,
                "chatbot_questions": []
              },
              "legal_analysis": null,
              "graph_data": {}
            }
            """;

        GraphRagResponse response = mapper.readValue(graphRagJson, GraphRagResponse.class);

        assertNull(response.getLegalAnalysis(),
                "Complete sözleşmede legal_analysis null olabilmeli — DTO bunu kabul etmeli");
        assertEquals("complete", response.getSuggestions().getStatus());
    }

    @Test
    @DisplayName("GraphRAG kontrat: snake_case JSON alan adları @JsonProperty ile Java camelCase'e dönüşüyor")
    void graphrag_contract_snake_case_to_camel_case_mapping() throws Exception {
        String json = """
            {
              "analysis": {
                "contract_type": "kira_sozlesmesi",
                "completeness_score": 75.0,
                "compliance_score": 60.0,
                "needs_llm_analysis": false,
                "matched_fields": [],
                "missing_required": [],
                "missing_optional": [],
                "validation_errors": []
              },
              "suggestions": {"status": "complete", "next_action": null, "chatbot_questions": []},
              "legal_analysis": null,
              "graph_data": {}
            }
            """;

        GraphRagResponse resp = mapper.readValue(json, GraphRagResponse.class);

        // snake_case → camelCase dönüşümü @JsonProperty ile sağlanmalı
        assertNotNull(resp.getAnalysis().getCompletenessScore(),  "completeness_score map edilmeli");
        assertNotNull(resp.getAnalysis().getComplianceScore(),    "compliance_score map edilmeli");
        assertNotNull(resp.getAnalysis().getMissingRequired(),    "missing_required map edilmeli");
        assertNotNull(resp.getAnalysis().getMissingOptional(),    "missing_optional map edilmeli");
        assertNotNull(resp.getAnalysis().getMatchedFields(),      "matched_fields map edilmeli");
        assertNotNull(resp.getAnalysis().getValidationErrors(),   "validation_errors map edilmeli");
    }
}
