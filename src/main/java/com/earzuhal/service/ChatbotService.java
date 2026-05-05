package com.earzuhal.service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.dto.analysis.GraphRagResponse;
import com.earzuhal.dto.chatbot.ChatIntentResponse;
import com.earzuhal.dto.chatbot.ChatRequest;
import com.earzuhal.dto.chatbot.ChatResponse;
import com.earzuhal.dto.chatbot.EnrichedChatRequest;
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

    /**
     * "Sözleşmelerim hakkında bilgi ver", "oluşturduğum sözleşmeler", "benim
     * tüm sözleşmelerim" gibi LİSTE sorgularını yakalamak için anahtar kelimeler.
     * NLP intent classifier bu tür ifadeleri "GENERAL_HELP" olarak etiketliyor;
     * o yüzden intent gelmeden önce bu pattern'i yakalayıp tüm sözleşmelerin
     * özetini contractContext olarak chatbot-server'a iletiyoruz.
     */
    private static boolean looksLikeContractListQuery(String message) {
        if (message == null) return false;
        String m = message.toLowerCase()
                .replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i')
                .replace('ö', 'o').replace('ş', 's').replace('ü', 'u');
        boolean mentionsContract = m.contains("sozlesme") || m.contains("contract");
        if (!mentionsContract) return false;
        return m.contains("benim")
                || m.contains("olusturdugum")
                || m.contains("olusturdugu")
                || m.contains("kendi sozlesme")
                || m.contains("sozlesmelerim")
                || m.contains("tum sozlesme")
                || m.contains("sozlesme listesi")
                || m.contains("hangi sozlesme")
                || m.contains("kac sozlesme")
                || m.contains("sahip oldugum");
    }

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

        // 0. "Sözleşmelerim hakkında bilgi ver" gibi LİSTE sorgularında
        // NLP intent'i (genelde GENERAL_HELP) yetersiz kalıyor — kullanıcının
        // tüm sözleşmelerinin özetini bağlama enjekte ediyoruz ve intent'i
        // CONTRACT_LIST_QUERY olarak işaretliyoruz. Chatbot-server bu intent
        // için system_override + contractContext ile LLM'e gider.
        if (looksLikeContractListQuery(request.getMessage())) {
            String allContracts = buildAllContractsContext(username);
            if (allContracts != null && !allContracts.isBlank()) {
                EnrichedChatRequest listEnriched = EnrichedChatRequest.builder()
                        .message(request.getMessage())
                        .sanitizedMessage(request.getMessage())
                        .intent("CONTRACT_LIST_QUERY")
                        .contractContext(allContracts)
                        .graphRagContext(null)
                        .history(request.getHistory())
                        .build();
                log.info("CONTRACT_LIST_QUERY: kullanıcının {} sözleşmesi context'e enjekte edildi",
                        allContracts.split("\n").length);
                return forwardToChatbot(listEnriched);
            }
        }

        // 1. NLP-server'a gönder → intent + sanitized_message
        ChatIntentResponse nlpResult = nlpService.classifyIntent(request.getMessage());
        log.info("NLP intent: {} (güven: {})", nlpResult.getIntent(), nlpResult.getConfidence());

        // 2. Sözleşme seçimi
        Contract activeContract = resolveContract(username, request.getContractId());

        // Intent sözleşme bağlamı gerektirip kullanıcının birden fazla sözleşmesi varsa
        // ve istek belirli bir contractId taşımıyorsa, kullanıcıya seçim yaptır.
        boolean intentNeedsContract = GRAPHRAG_INTENTS.contains(nlpResult.getIntent());
        if (intentNeedsContract && request.getContractId() == null) {
            List<Contract> selectable = listSelectableContracts(username);
            if (selectable.size() > 1) {
                ChatResponse selection = new ChatResponse(
                        "Hangi sözleşme hakkında konuşmak istediğinizi seçin. Cevabımı yalnızca seçtiğiniz sözleşmenin verileri üzerinden vereceğim.",
                        List.of()
                );
                selection.setRequiresContractSelection(true);
                selection.setContractOptions(selectable.stream()
                        .map(c -> new ChatResponse.ContractOption(
                                c.getId(),
                                c.getTitle(),
                                c.getType(),
                                c.getStatus()))
                        .toList());
                return selection;
            }
            if (selectable.size() == 1) {
                activeContract = selectable.get(0);
            }
        }

        String contractContext = buildContractContext(activeContract);

        // 3. Intent'e göre GraphRAG bağlamı al (koşullu)
        String graphRagContext = null;
        if (intentNeedsContract && activeContract != null) {
            graphRagContext = fetchGraphRagContext(activeContract);
        }

        // 4. Zenginleştirilmiş isteği chatbot-server'a gönder
        // KVKK: Ham mesaj YOK — yalnızca NLP tarafından sanitize edilmiş mesaj
        // Gemini'ye iletilir. NlpService sanitization yapılamadığında istisna fırlatır,
        // bu yüzden buraya geldiğimizde sanitizedMessage'ın dolu olduğu garantilidir.
        String safeMessage = nlpResult.getSanitizedMessage();
        EnrichedChatRequest enriched = EnrichedChatRequest.builder()
                .message(safeMessage)
                .sanitizedMessage(safeMessage)
                .intent(nlpResult.getIntent())
                .contractContext(contractContext)
                .graphRagContext(graphRagContext)
                .history(request.getHistory())
                .build();

        return forwardToChatbot(enriched);
    }

    /**
     * Kullanıcının açıkça istediği sözleşmeyi getirir; yoksa en son DRAFT/PENDING'i
     * fallback olarak kullanır. Erişim yetkisi: sahip ya da karşı taraf.
     */
    private Contract resolveContract(String username, Long contractId) {
        if (contractId != null) {
            try {
                User viewer = userService.getUserByUsernameOrEmail(username);
                Contract c = contractRepository.findById(contractId).orElse(null);
                if (c == null) return null;
                boolean isOwner = c.getUser().getUsername().equals(viewer.getUsername());
                boolean isCounterparty = viewer.getTcKimlik() != null
                        && c.getCounterpartyTcKimlik() != null
                        && viewer.getTcKimlik().equals(c.getCounterpartyTcKimlik());
                if (isOwner || isCounterparty) return c;
                log.warn("Chatbot: kullanıcı {} sözleşme {} üzerinde yetkili değil", username, contractId);
                return null;
            } catch (Exception e) {
                log.warn("Chatbot resolveContract hatası: {}", e.getMessage());
                return null;
            }
        }
        return findActiveContract(username);
    }

    /** Kullanıcının chatbot'ta seçebileceği sözleşmeler — son 10 sözleşme (DRAFT/PENDING/APPROVED). */
    private List<Contract> listSelectableContracts(String username) {
        try {
            User user = userService.getUserByUsernameOrEmail(username);
            return contractRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                    .filter(c -> {
                        String s = c.getStatus();
                        return s == null
                                || "DRAFT".equals(s)
                                || "PENDING".equals(s)
                                || "APPROVED".equals(s);
                    })
                    .limit(10)
                    .toList();
        } catch (Exception e) {
            log.warn("listSelectableContracts hatası: {}", e.getMessage());
            return List.of();
        }
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

    /**
     * Kullanıcının TÜM sözleşmelerinin özet listesini Gemini için string'e
     * dönüştürür. CONTRACT_LIST_QUERY intent'inde kullanılır — kullanıcı
     * "sözleşmelerim hakkında bilgi ver" derken Gemini bu listeyi görüp
     * hangileri olduğunu, durumlarını ve tarafları söyleyebilir.
     */
    private String buildAllContractsContext(String username) {
        try {
            User user = userService.getUserByUsernameOrEmail(username);
            List<Contract> all = contractRepository
                    .findByUserIdOrderByCreatedAtDesc(user.getId());
            if (all.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("Kullanıcının kayıtlı sözleşmeleri (toplam ")
              .append(all.size()).append("):\n");
            int i = 1;
            for (Contract c : all) {
                if (i > 20) break; // 20 ile sınırla — prompt boyutu kontrolü
                sb.append(i++).append(". ")
                  .append(c.getTitle() == null ? "(başlıksız)" : c.getTitle())
                  .append(" | tip=").append(c.getType() == null ? "?" : c.getType())
                  .append(" | durum=").append(c.getStatus() == null ? "?" : c.getStatus());
                if (c.getCounterpartyName() != null && !c.getCounterpartyName().isBlank()) {
                    sb.append(" | karşı taraf=").append(c.getCounterpartyName());
                }
                if (c.getAmount() != null && !c.getAmount().isBlank()) {
                    sb.append(" | tutar=").append(c.getAmount());
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("buildAllContractsContext hatası: {}", e.getMessage());
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

    /**
     * Chatbot için legal/clause bağlamını kurar. Daha önce graphRagService.analyze()
     * çağrılıyordu — bu, graphrag-server tarafında Gemini'yi tetikliyor ve isteğin
     * 2-4 dakika sürmesine yol açıyordu (Gemini yavaş + main-server netty timeout 120s).
     * Sonuç: chatbot hiç yanıt veremeden mobil 30s'de timeout'a düşüyor ve
     * "Bir hata oluştu lütfen tekrar deneyin" gösteriyordu.
     *
     * Çözüm: GraphRAG'i hot path'ten çıkar. Sözleşme oluşturulurken Gemini'nin
     * ürettiği `missingClauseExplanations` JSON'u zaten Contract üzerinde kayıtlı —
     * chatbot context'i bu cached veriden kuruyor. GraphRAG'e canlı çağrı yapılmıyor;
     * gerekirse ayrı bir asenkron job ile zenginleştirilebilir.
     */
    private String fetchGraphRagContext(Contract contract) {
        try {
            String missing = contract.getMissingClauseExplanations();
            if (missing == null || missing.isBlank()) return null;

            // JSON listesi: [{field, riskLevel, tbkArticle, explanation, suggestion}, ...]
            // Tip-belirsiz parse — chatbot'un göreceği özet metni üret.
            com.fasterxml.jackson.databind.JsonNode arr;
            try {
                arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(missing);
            } catch (Exception parseErr) {
                log.warn("missingClauseExplanations parse edilemedi: {}", parseErr.getMessage());
                return null;
            }
            if (!arr.isArray() || arr.size() == 0) return null;

            StringBuilder sb = new StringBuilder();
            sb.append("Eksik / dikkat edilecek maddeler (sözleşme oluşturulurken AI tarafından tespit edildi):\n");
            int max = Math.min(arr.size(), 8);
            for (int i = 0; i < max; i++) {
                var n = arr.get(i);
                String field = n.path("field").asText("");
                String level = n.path("riskLevel").asText("");
                int tbk = n.path("tbkArticle").asInt(0);
                String exp = n.path("explanation").asText("");
                sb.append("- ").append(field);
                if (!level.isBlank()) sb.append(" (").append(level).append(")");
                if (tbk > 0) sb.append(" — TBK madde ").append(tbk);
                if (!exp.isBlank()) sb.append(": ").append(exp);
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Chatbot legal context kurulamadı: {}", e.getMessage());
            return null;
        }
    }

    /** Zenginleştirilmiş isteği chatbot-server'a iletir. */
    private ChatResponse forwardToChatbot(EnrichedChatRequest enriched) {
        try {
            ChatResponse resp = chatbotWebClient.post()
                    .uri("/api/chat")
                    .bodyValue(enriched)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block();
            // Boş yanıt geldiyse (timeout / Gemini hatası) kullanıcıya
            // anlamlı bir mesaj göster — frontend "Bir hata oluştu" yerine
            // bağlamsal bir mesaj görür.
            if (resp == null || resp.getResponse() == null || resp.getResponse().isBlank()) {
                return fallbackResponse();
            }
            return resp;
        } catch (WebClientResponseException e) {
            log.error("Chatbot servis hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return fallbackResponse();
        } catch (Exception e) {
            log.error("Chatbot servise bağlanılamadı: {}", e.getMessage());
            return fallbackResponse();
        }
    }

    /** Chatbot erişilemez/zaman aşımı durumunda kullanıcıya şeffaf, hatalı görünmeyen yanıt. */
    private ChatResponse fallbackResponse() {
        ChatResponse fallback = new ChatResponse(
                "Yardımcı asistan şu anda çok yoğun ve yanıt veremedi. Lütfen birkaç saniye sonra tekrar deneyin. " +
                        "Bu arada sözleşmenizi 'Sözleşmelerim' sayfasından açıp 'Bulunması Gereken Maddeler' panelinden " +
                        "rehberi inceleyebilirsiniz.",
                List.of(
                        "Sözleşme nasıl oluşturulur?",
                        "PDF nasıl indirilir?",
                        "Hangi sözleşme tipleri destekleniyor?"
                )
        );
        return fallback;
    }
}
