package com.earzuhal.service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.Repository.IdentityVerificationRepository;
import com.earzuhal.Repository.UserRepository;
import com.earzuhal.dto.analysis.ContractTypeMapping;
import com.earzuhal.dto.contract.ContractRequest;
import com.earzuhal.dto.contract.ContractResponse;
import com.earzuhal.dto.contract.ContractStatsResponse;
import com.earzuhal.dto.contract.PdfConfirmResponse;
import com.earzuhal.dto.explanation.ClauseExplanationItem;
import com.earzuhal.dto.explanation.ContractExplanationResponse;
import com.earzuhal.exception.BadRequestException;
import com.earzuhal.exception.ResourceNotFoundException;
import com.earzuhal.exception.UnauthorizedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final UserService userService;
    private final DisclaimerService disclaimerService;
    private final ExplanationService explanationService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private final TcKimlikEncryptionService encryptionService;
    private final StatisticsService statisticsService;

    public ContractService(ContractRepository contractRepository, UserRepository userRepository,
                           IdentityVerificationRepository verificationRepository,
                           UserService userService, DisclaimerService disclaimerService,
                           ExplanationService explanationService,
                           NotificationService notificationService,
                           TcKimlikEncryptionService encryptionService,
                           StatisticsService statisticsService,
                           ObjectMapper objectMapper) {
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.userService = userService;
        this.disclaimerService = disclaimerService;
        this.explanationService = explanationService;
        this.notificationService = notificationService;
        this.encryptionService = encryptionService;
        this.statisticsService = statisticsService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContractResponse create(ContractRequest request, String username) {
        User user = userService.getUserByUsernameOrEmail(username);

        Contract contract = new Contract();
        contract.setTitle(request.getTitle());
        contract.setType(request.getType());
        contract.setContent(request.getContent());
        contract.setAmount(request.getAmount());
        contract.setCounterpartyName(request.getCounterpartyName());
        contract.setCounterpartyRole(request.getCounterpartyRole());
        // Karşı taraf TC Kimlik şifreli saklanır
        contract.setCounterpartyTcKimlik(encryptionService.encrypt(request.getCounterpartyTcKimlik()));
        contract.setStatus("DRAFT");
        contract.setUser(user);
        contract.setCreatedAt(OffsetDateTime.now());
        contract.setUpdatedAt(OffsetDateTime.now());

        // Analiz bağlamı varsa madde açıklamaları üret ve JSON olarak sakla
        if (request.getAnalysisContext() != null) {
            try {
                List<ClauseExplanationItem> explanations =
                        explanationService.generate(request.getAnalysisContext());
                contract.setClauseExplanations(objectMapper.writeValueAsString(explanations));
            } catch (Exception e) {
                log.warn("Madde açıklamaları üretilemedi, sözleşme açıklamasız oluşturuluyor: {}", e.getMessage());
            }
        }

        Contract saved = contractRepository.save(contract);
        return convertToResponse(saved);
    }

    public List<ContractResponse> getAllByUser(String username) {
        User user = userService.getUserByUsernameOrEmail(username);

        // Sahip olunan sözleşmeler + karşı taraf olarak yer aldığı sözleşmeler
        Map<Long, Contract> merged = new LinkedHashMap<>();
        contractRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .forEach(c -> merged.put(c.getId(), c));

        if (user.getTcKimlik() != null) {
            contractRepository.findByCounterpartyTcKimlikOrderByCreatedAtDesc(user.getTcKimlik())
                    .forEach(c -> merged.putIfAbsent(c.getId(), c));
        }

        return merged.values().stream()
                .map(c -> convertToResponse(c, user))
                .collect(Collectors.toList());
    }

    public ContractResponse getById(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));
        User viewer = userService.getUserByUsernameOrEmail(username);
        verifyReadAccess(contract, viewer);
        return convertToResponse(contract, viewer);
    }

    /** PDF üretimi için tam entity döndürür (user lazy-load dahil) */
    public Contract getEntityById(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));
        verifyOwnership(contract, username);
        return contract;
    }

    /**
     * Sözleşmedeki her maddenin neden eklendiğini, hangi kanun maddesine dayandığını
     * ve istatistiksel yaygınlığını açıklayan yanıtı döndürür.
     */
    public ContractExplanationResponse getExplanation(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));
        User viewer = userService.getUserByUsernameOrEmail(username);
        verifyReadAccess(contract, viewer);

        List<ClauseExplanationItem> clauses = Collections.emptyList();
        if (contract.getClauseExplanations() != null && !contract.getClauseExplanations().isBlank()) {
            try {
                clauses = objectMapper.readValue(
                        contract.getClauseExplanations(),
                        new TypeReference<List<ClauseExplanationItem>>() {});
            } catch (Exception e) {
                log.warn("Madde açıklamaları deserialize edilemedi, contract id={}: {}", id, e.getMessage());
            }
        }

        return ContractExplanationResponse.builder()
                .contractId(contract.getId())
                .contractTitle(contract.getTitle())
                .contractType(contract.getType())
                .clauses(clauses)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    @Transactional
    public ContractResponse update(Long id, ContractRequest request, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));
        verifyOwnership(contract, username);

        // Onaylanmış / reddedilmiş sözleşmeler düzenlenemez
        if ("APPROVED".equals(contract.getStatus()) || "REJECTED".equals(contract.getStatus())) {
            throw new BadRequestException("Sonuçlandırılmış sözleşmeler düzenlenemez.");
        }

        if (request.getTitle() != null) contract.setTitle(request.getTitle());
        if (request.getType() != null) contract.setType(request.getType());
        if (request.getContent() != null) contract.setContent(request.getContent());
        if (request.getAmount() != null) contract.setAmount(request.getAmount());
        if (request.getCounterpartyName() != null) contract.setCounterpartyName(request.getCounterpartyName());
        if (request.getCounterpartyRole() != null) contract.setCounterpartyRole(request.getCounterpartyRole());
        if (request.getCounterpartyTcKimlik() != null)
            contract.setCounterpartyTcKimlik(encryptionService.encrypt(request.getCounterpartyTcKimlik()));
        contract.setUpdatedAt(OffsetDateTime.now());

        Contract updated = contractRepository.save(contract);
        User viewer = userService.getUserByUsernameOrEmail(username);
        return convertToResponse(updated, viewer);
    }

    @Transactional
    public void delete(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));
        verifyOwnership(contract, username);
        // Onaylanmış sözleşmeler silinemez (yasal kayıt)
        if ("APPROVED".equals(contract.getStatus())) {
            throw new BadRequestException("Onaylanmış sözleşmeler silinemez.");
        }
        contractRepository.delete(contract);
    }

    @Transactional
    public ContractResponse finalize(Long id, String username) {
        if (!disclaimerService.hasAccepted(username)) {
            throw new BadRequestException("Sözleşme onaya gönderilebilmesi için yasal uyarıyı kabul etmeniz gerekmektedir");
        }
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));
        verifyOwnership(contract, username);
        contract.setStatus("PENDING");
        contract.setUpdatedAt(OffsetDateTime.now());
        Contract updated = contractRepository.save(contract);

        if (contract.getCounterpartyTcKimlik() != null && !contract.getCounterpartyTcKimlik().isBlank()) {
            userRepository.findByTcKimlik(contract.getCounterpartyTcKimlik())
                .ifPresent(counterparty -> notificationService.notifyUser(
                    counterparty,
                    "CONTRACT_PENDING_APPROVAL",
                    "Yeni onay bekleyen sözleşme",
                    "" + contract.getTitle() + " sözleşmesi onayınızı bekliyor.",
                    contract.getId()
                ));
        }

        User viewer = userService.getUserByUsernameOrEmail(username);
        return convertToResponse(updated, viewer);
    }

    public List<ContractResponse> getPendingApprovals(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        // Kullanıcının TC Kimliği yoksa (doğrulanmamış) boş liste dön
        if (user.getTcKimlik() == null) {
            return Collections.emptyList();
        }
        // Karşı taraf TC'si ile eşleşen PENDING sözleşmeleri getir
        return contractRepository
                .findByCounterpartyTcKimlikAndStatusOrderByCreatedAtDesc(user.getTcKimlik(), "PENDING")
                .stream()
                .map(c -> convertToResponse(c, user))
                .collect(Collectors.toList());
    }

    public ContractStatsResponse getStats(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        Long userId = user.getId();

        return ContractStatsResponse.builder()
                .totalCount(contractRepository.countByUserId(userId))
                .draftCount(contractRepository.countByUserIdAndStatus(userId, "DRAFT"))
                .pendingCount(contractRepository.countByUserIdAndStatus(userId, "PENDING"))
                .approvedCount(contractRepository.countByUserIdAndStatus(userId, "APPROVED"))
                .completedCount(contractRepository.countByUserIdAndStatus(userId, "COMPLETED"))
                .rejectedCount(contractRepository.countByUserIdAndStatus(userId, "REJECTED"))
                .build();
    }

    @Transactional
    public ContractResponse approve(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));

        // D5: idempotency check
        if ("APPROVED".equals(contract.getStatus()) || "REJECTED".equals(contract.getStatus())) {
            throw new BadRequestException("Sözleşme zaten sonuçlandırılmış");
        }

        // D4: self-approval prevention
        if (contract.getUser().getUsername().equals(username)) {
            throw new UnauthorizedException("Kendi sözleşmenizi onaylayamazsınız");
        }

        // Kimlik doğrulama gate — hem User.tcKimlik hem de IdentityVerification kaydı kontrol edilir
        User approver = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: " + username));
        validateIdentityForApproval(approver);
        verifyCounterpartyAccess(contract, approver, "onay");

        contract.setStatus("APPROVED");
        contract.setUpdatedAt(OffsetDateTime.now());
        Contract saved = contractRepository.save(contract);

        notificationService.notifyUser(
            contract.getUser(),
            "CONTRACT_APPROVED",
            "Sözleşme onaylandı",
            "" + contract.getTitle() + " sözleşmeniz karşı taraf tarafından onaylandı.",
            contract.getId()
        );

        statisticsService.recordOutcomeAsync(
            ContractTypeMapping.toTurkish(contract.getType()), true);

        return convertToResponse(saved, approver);
    }

    @Transactional
    public ContractResponse reject(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));

        // D5: idempotency check
        if ("APPROVED".equals(contract.getStatus()) || "REJECTED".equals(contract.getStatus())) {
            throw new BadRequestException("Sözleşme zaten sonuçlandırılmış");
        }

        // D4: self-rejection prevention
        if (contract.getUser().getUsername().equals(username)) {
            throw new UnauthorizedException("Kendi sözleşmenizi reddederek iptal edemezsiniz; bunun yerine sözleşmeyi silin");
        }

        // Kimlik doğrulama gate — hem User.tcKimlik hem de IdentityVerification kaydı kontrol edilir
        User approver = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: " + username));
        validateIdentityForApproval(approver);
        verifyCounterpartyAccess(contract, approver, "ret");

        contract.setStatus("REJECTED");
        contract.setUpdatedAt(OffsetDateTime.now());
        Contract saved = contractRepository.save(contract);

        notificationService.notifyUser(
            contract.getUser(),
            "CONTRACT_REJECTED",
            "Sözleşme reddedildi",
            "" + contract.getTitle() + " sözleşmeniz karşı taraf tarafından reddedildi.",
            contract.getId()
        );

        statisticsService.recordOutcomeAsync(
            ContractTypeMapping.toTurkish(contract.getType()), false);

        return convertToResponse(saved, approver);
    }

    /**
     * Kimlik doğrulama gate:
     * 1. User.tcKimlik null olmamalı
     * 2. IdentityVerification kaydı VERIFIED statüsünde olmalı
     * Her iki koşul da sağlanmazsa BadRequestException fırlatır.
     */
    private void validateIdentityForApproval(User approver) {
        if (approver.getTcKimlik() == null) {
            throw new BadRequestException(
                    "Sözleşme işlemi için önce Kimlik Doğrulama sayfasından kimliğinizi doğrulamanız gerekmektedir");
        }
        // IdentityVerification kaydının da VERIFIED olduğunu doğrula
        var verification = verificationRepository.findByUserId(approver.getId());
        if (verification.isEmpty() || !"VERIFIED".equals(verification.get().getStatus())) {
            throw new BadRequestException(
                    "Kimlik doğrulama kaydınız bulunamadı veya onaylanmamış. Lütfen Kimlik Doğrulama sayfasını ziyaret edin.");
        }
    }

    /** Onay/ret işlemlerinde karşı taraf eşleştirmesini zorunlu tutar. */
    private void verifyCounterpartyAccess(Contract contract, User approver, String actionName) {
        String counterpartyTcKimlik = contract.getCounterpartyTcKimlik();
        if (counterpartyTcKimlik == null || counterpartyTcKimlik.isBlank()) {
            throw new UnauthorizedException("Karşı taraf bilgisi eksik olduğu için sözleşme " + actionName + " işlemi yapılamaz");
        }
        if (!counterpartyTcKimlik.equals(approver.getTcKimlik())) {
            throw new UnauthorizedException("Bu sözleşmeyi " + actionName + " yetkisine sahip değilsiniz");
        }
    }

    /**
     * PDF oluşturmadan önce kullanıcıya gösterilecek onay verisi.
     * NLP parse'ının doğruluğunu kullanıcının teyit etmesini sağlar.
     */
    public PdfConfirmResponse getPdfConfirmData(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sözleşme bulunamadı, id: " + id));
        verifyOwnership(contract, username);

        java.util.List<String> warnings = new java.util.ArrayList<>();

        if (contract.getAmount() == null || contract.getAmount().isBlank()) {
            warnings.add("Tutar alanı boş — NLP bu bilgiyi metinden çıkaramadı. Sözleşmeyi güncelleyerek tutarı ekleyebilirsiniz.");
        }
        if (contract.getCounterpartyName() == null || contract.getCounterpartyName().isBlank()) {
            warnings.add("Karşı taraf adı eksik.");
        }
        if (contract.getCounterpartyTcKimlik() == null || contract.getCounterpartyTcKimlik().isBlank()) {
            warnings.add("Karşı taraf TC Kimlik numarası girilmemiş — dijital onay akışı çalışmaz.");
        }
        if (contract.getContent() == null || contract.getContent().length() < 50) {
            warnings.add("Sözleşme içeriği çok kısa — parse hatası olmuş olabilir.");
        }

        String typeLabel = typeLabelTr(contract.getType());
        String preview = contract.getContent() != null
                ? contract.getContent().substring(0, Math.min(300, contract.getContent().length()))
                : "";

        PdfConfirmResponse.PartyInfo ownerInfo = PdfConfirmResponse.PartyInfo.builder()
                .displayName(contract.getUser().getFirstName() != null
                        ? contract.getUser().getFirstName() + " " + contract.getUser().getLastName()
                        : contract.getUser().getUsername())
                .role("Sözleşme Sahibi")
                .build();

        PdfConfirmResponse.PartyInfo counterpartyInfo = PdfConfirmResponse.PartyInfo.builder()
                .displayName(contract.getCounterpartyName())
                .role(contract.getCounterpartyRole())
                .tcMasked(encryptionService.decryptAndMask(contract.getCounterpartyTcKimlik()))
                .build();

        return PdfConfirmResponse.builder()
                .contractId(contract.getId())
                .contractType(typeLabel)
                .title(contract.getTitle())
                .status(contract.getStatus())
                .owner(ownerInfo)
                .counterparty(counterpartyInfo)
                .amount(contract.getAmount())
                .amountPresent(contract.getAmount() != null && !contract.getAmount().isBlank())
                .contentPreview(preview)
                .contentLength(contract.getContent() != null ? contract.getContent().length() : 0)
                .warnings(warnings)
                .readyForPdf(warnings.isEmpty())
                .build();
    }

    private String typeLabelTr(String type) {
        if (type == null) return "Bilinmiyor";
        return switch (type.toUpperCase()) {
            case "RENTAL"           -> "Kira Sözleşmesi";
            case "SALES"            -> "Satış Sözleşmesi";
            case "SERVICE"          -> "Hizmet Sözleşmesi";
            case "EMPLOYMENT"       -> "İş Sözleşmesi";
            case "LOAN"             -> "Borç Sözleşmesi";
            case "POWER_OF_ATTORNEY"-> "Vekaletname";
            case "COMMITMENT"       -> "Taahhütname";
            case "SURETY"           -> "Kefalet Sözleşmesi";
            case "NDA"              -> "Gizlilik Sözleşmesi";
            default                 -> type;
        };
    }

    /** Sözleşme sahibi değilse 404 döner (kaynak maskeleme ile IDOR önleme) */
    private void verifyOwnership(Contract contract, String username) {
        if (!contract.getUser().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Sözleşme bulunamadı, id: " + contract.getId());
        }
    }

    /**
     * Okuma erişimi: ya sahip ya da karşı taraf (TC eşleşmesi). Her ikisi de değilse 404.
     */
    private void verifyReadAccess(Contract contract, User viewer) {
        boolean isOwner = contract.getUser().getUsername().equals(viewer.getUsername());
        boolean isCounterparty = viewer.getTcKimlik() != null
                && contract.getCounterpartyTcKimlik() != null
                && viewer.getTcKimlik().equals(contract.getCounterpartyTcKimlik());
        if (!isOwner && !isCounterparty) {
            throw new ResourceNotFoundException("Sözleşme bulunamadı, id: " + contract.getId());
        }
    }

    private ContractResponse convertToResponse(Contract contract) {
        return convertToResponse(contract, null);
    }

    private ContractResponse convertToResponse(Contract contract, User viewer) {
        // Karşı taraf TC: API response'da her zaman maskeli göster (123******01)
        String maskedCounterpartyTc = encryptionService.decryptAndMask(contract.getCounterpartyTcKimlik());
        boolean isOwner = viewer == null || contract.getUser().getUsername().equals(viewer.getUsername());
        return ContractResponse.builder()
                .id(contract.getId())
                .title(contract.getTitle())
                .type(contract.getType())
                .status(contract.getStatus())
                .content(contract.getContent())
                .amount(contract.getAmount())
                .counterpartyName(contract.getCounterpartyName())
                .counterpartyRole(contract.getCounterpartyRole())
                .counterpartyTcKimlik(maskedCounterpartyTc)
                .userId(contract.getUser().getId())
                .ownerUsername(contract.getUser().getUsername())
                .viewerIsOwner(isOwner)
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
