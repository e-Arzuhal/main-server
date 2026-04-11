package com.earzuhal.Service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.dto.analysis.GraphRagResponse;
import com.earzuhal.dto.chatbot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Set;

@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private final WebClient chatbotWebClient;
    private final NlpService nlpService;
    private final GraphRagService graphRagService;
    private final UserService userService;
    private final ContractRepository contractRepository;

    /** Intent'ler — GraphRAG bağlamı gerektiren */
    private static final Set<String> GRAPHRAG_INTENTS = Set.of(
            "CONTRACT_CLAUSE_QUESTION",
            "MISSING_CLAUSE_QUESTION",
            "LAW_REFERENCE"
    );

    public ChatbotService(
            @Qualifier("chatbotWebClient") WebClient chatbotWebClient,
            NlpService nlpService,
            GraphRagService graphRagService,
            UserService userService,
            ContractRepository contractRepository) {
        this.chatbotWebClient = chatbotWebClient;
        this.nlpService = nlpService;
        this.graphRagService = graphRagService;
        this.userService = userService;
        this.contractRepository = contractRepository;
    }

    /**
     * Chatbot orkestrasyon pipeline:
     * 1. NLP-server → intent + PII maskeleme
     * 2. Aktif sözleşme bağlamı (JWT token'dan)
     * 3. GraphRAG bağlamı (koşullu)
     * 4. Chatbot-server → Gemini ile nihai yanıt
     */
    public ChatResponse chat(ChatRequest request, String username) {
        log.info("Chatbot orkestrasyon başlıyor — user={}, mesaj={}", username, request.getMessage());

        // 1. NLP-server'a gönder → intent + sanitized_message
        ChatIntentResponse nlpResult = nlpService.classifyIntent(request.getMessage());
        log.info("NLP intent: {} (güven: {})", nlpResult.getIntent(), nlpResult.getConfidence());

        // 2. Kullanıcının aktif sözleşmesini bul (JWT'den username)
        Contract activeContract = findActiveContract(username);
        String contractContext = buildContractContext(activeContract);

        // 3. Intent'e göre GraphRAG bağlamı al (koşullu)
        String graphRagContext = null;
        if (GRAPHRAG_INTENTS.contains(nlpResult.getIntent()) && activeContract != null) {
            graphRagContext = fetchGraphRagContext(activeContract);
        }

        // 4. Zenginleştirilmiş isteği chatbot-server'a gönder
        EnrichedChatRequest enriched = EnrichedChatRequest.builder()
                .message(request.getMessage())
                .sanitizedMessage(nlpResult.getSanitizedMessage())
                .intent(nlpResult.getIntent())
                .contractContext(contractContext)
                .graphRagContext(graphRagContext)
                .history(request.getHistory())
                .build();

        return forwardToChatbot(enriched);
    }

    /** Kullanıcının son DRAFT veya PENDING sözleşmesini bulur. */
    private Contract findActiveContract(String username) {
        try {
            User user = userService.getUserByUsernameOrEmail(username);
            // Önce DRAFT, sonra PENDING ara
            List<Contract> drafts = contractRepository
                    .findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "DRAFT");
            if (!drafts.isEmpty()) return drafts.get(0);

            List<Contract> pending = contractRepository
                    .findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING");
            if (!pending.isEmpty()) return pending.get(0);

            return null;
        } catch (Exception e) {
            log.warn("Aktif sözleşme bulunamadı: {}", e.getMessage());
            return null;
        }
    }

    /** Sözleşme bağlam özetini oluşturur. */
    private String buildContractContext(Contract contract) {
        if (contract == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("Sözleşme Tipi: ").append(contract.getType()).append("\n");
        sb.append("Başlık: ").append(contract.getTitle()).append("\n");
        sb.append("Durum: ").append(contract.getStatus()).append("\n");
        if (contract.getCounterpartyName() != null) {
            sb.append("Karşı Taraf: ").append(contract.getCounterpartyName()).append("\n");
        }
        if (contract.getAmount() != null) {
            sb.append("Tutar: ").append(contract.getAmount()).append("\n");
        }
        // İçerik özeti (ilk 500 karakter)
        if (contract.getContent() != null) {
            String contentPreview = contract.getContent().length() > 500
                    ? contract.getContent().substring(0, 500) + "..."
                    : contract.getContent();
            sb.append("İçerik: ").append(contentPreview);
        }
        return sb.toString();
    }

    /** GraphRAG'den sözleşme bağlam bilgisi alır. */
    private String fetchGraphRagContext(Contract contract) {
        try {
            String contractType = contract.getType();
            if (contractType == null) return null;

            // Türkçe tip dönüşümü (GraphRAG Türkçe tip bekler)
            String turkishType = com.earzuhal.dto.analysis.ContractTypeMapping.toTurkish(contractType);
            if (turkishType == null) turkishType = contractType;

            GraphRagResponse graphRag = graphRagService.analyze(turkishType, java.util.Map.of());

            if (graphRag == null) return null;

            StringBuilder sb = new StringBuilder();

            // Analiz sonucu
            if (graphRag.getAnalysis() != null) {
                var analysis = graphRag.getAnalysis();
                if (analysis.getMatchedFields() != null && !analysis.getMatchedFields().isEmpty()) {
                    sb.append("Eşleşen Maddeler: ").append(String.join(", ", analysis.getMatchedFields())).append("\n");
                }
                if (analysis.getMissingRequired() != null && !analysis.getMissingRequired().isEmpty()) {
                    sb.append("Eksik Zorunlu Maddeler: ").append(String.join(", ", analysis.getMissingRequired())).append("\n");
                }
            }

            // Hukuki analiz
            if (graphRag.getLegalAnalysis() != null) {
                var legal = graphRag.getLegalAnalysis();
                if (legal.getTbkArticles() != null && !legal.getTbkArticles().isEmpty()) {
                    sb.append("İlgili TBK Maddeleri: ").append(legal.getTbkArticles()).append("\n");
                }
                if (legal.getGeneralAssessment() != null) {
                    sb.append("Genel Değerlendirme: ").append(legal.getGeneralAssessment()).append("\n");
                }
                if (legal.getRisks() != null) {
                    for (var risk : legal.getRisks()) {
                        sb.append("Risk: ").append(risk.getField())
                                .append(" (").append(risk.getRiskLevel()).append(") — ")
                                .append(risk.getExplanation()).append("\n");
                    }
                }
            }

            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            log.warn("GraphRAG bağlam alınamadı: {}", e.getMessage());
            return null;
        }
    }

    /** Zenginleştirilmiş isteği chatbot-server'a iletir. */
    private ChatResponse forwardToChatbot(EnrichedChatRequest enriched) {
        try {
            return chatbotWebClient.post()
                    .uri("/api/chat")
                    .bodyValue(enriched)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Chatbot servis hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Chatbot servisi yanıt vermedi: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Chatbot servise bağlanılamadı: {}", e.getMessage());
            throw new RuntimeException("Chatbot servise bağlanılamadı. Servisin çalıştığından emin olun.", e);
        }
    }
}
