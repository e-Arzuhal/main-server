package com.earzuhal.Service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.Repository.UserRepository;
import com.earzuhal.dto.contract.ContractRequest;
import com.earzuhal.dto.contract.ContractResponse;
import com.earzuhal.dto.contract.ContractStatsResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DisclaimerService disclaimerService;
    private final ExplanationService explanationService;
    private final ObjectMapper objectMapper;

    public ContractService(ContractRepository contractRepository, UserRepository userRepository,
                           UserService userService, DisclaimerService disclaimerService,
                           ExplanationService explanationService, ObjectMapper objectMapper) {
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.disclaimerService = disclaimerService;
        this.explanationService = explanationService;
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
        contract.setCounterpartyTcKimlik(request.getCounterpartyTcKimlik());
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
        return contractRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public ContractResponse getById(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        verifyOwnership(contract, username);
        return convertToResponse(contract);
    }

    /** PDF üretimi için tam entity döndürür (user lazy-load dahil) */
    public Contract getEntityById(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        verifyOwnership(contract, username);
        return contract;
    }

    /**
     * Sözleşmedeki her maddenin neden eklendiğini, hangi kanun maddesine dayandığını
     * ve istatistiksel yaygınlığını açıklayan yanıtı döndürür.
     */
    public ContractExplanationResponse getExplanation(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        verifyOwnership(contract, username);

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
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        verifyOwnership(contract, username);

        if (request.getTitle() != null) contract.setTitle(request.getTitle());
        if (request.getType() != null) contract.setType(request.getType());
        if (request.getContent() != null) contract.setContent(request.getContent());
        if (request.getAmount() != null) contract.setAmount(request.getAmount());
        if (request.getCounterpartyName() != null) contract.setCounterpartyName(request.getCounterpartyName());
        if (request.getCounterpartyRole() != null) contract.setCounterpartyRole(request.getCounterpartyRole());
        if (request.getCounterpartyTcKimlik() != null) contract.setCounterpartyTcKimlik(request.getCounterpartyTcKimlik());
        contract.setUpdatedAt(OffsetDateTime.now());

        Contract updated = contractRepository.save(contract);
        return convertToResponse(updated);
    }

    @Transactional
    public void delete(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        verifyOwnership(contract, username);
        contractRepository.delete(contract);
    }

    @Transactional
    public ContractResponse finalize(Long id, String username) {
        if (!disclaimerService.hasAccepted(username)) {
            throw new BadRequestException("Sözleşme onaya gönderilebilmesi için yasal uyarıyı kabul etmeniz gerekmektedir");
        }
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        verifyOwnership(contract, username);
        contract.setStatus("PENDING");
        contract.setUpdatedAt(OffsetDateTime.now());
        Contract updated = contractRepository.save(contract);
        return convertToResponse(updated);
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
                .map(this::convertToResponse)
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
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));

        // D5: idempotency check
        if ("APPROVED".equals(contract.getStatus()) || "REJECTED".equals(contract.getStatus())) {
            throw new BadRequestException("Sözleşme zaten sonuçlandırılmış");
        }

        // D4: self-approval prevention
        if (contract.getUser().getUsername().equals(username)) {
            throw new UnauthorizedException("Kendi sözleşmenizi onaylayamazsınız");
        }

        // Kimlik doğrulama gate — onaylamak için doğrulanmış olmalı
        User approver = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Approver not found: " + username));
        if (approver.getTcKimlik() == null) {
            throw new BadRequestException(
                    "Sözleşme onaylamak için önce Kimlik Doğrulama sayfasından kimliğinizi doğrulamanız gerekmektedir");
        }

        // D4: TC Kimlik counterparty validation
        if (contract.getCounterpartyTcKimlik() != null &&
                !approver.getTcKimlik().equals(contract.getCounterpartyTcKimlik())) {
            throw new UnauthorizedException("Bu sözleşmeyi onaylama yetkisine sahip değilsiniz");
        }

        contract.setStatus("APPROVED");
        contract.setUpdatedAt(OffsetDateTime.now());
        return convertToResponse(contractRepository.save(contract));
    }

    @Transactional
    public ContractResponse reject(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));

        // D5: idempotency check
        if ("APPROVED".equals(contract.getStatus()) || "REJECTED".equals(contract.getStatus())) {
            throw new BadRequestException("Sözleşme zaten sonuçlandırılmış");
        }

        // D4: self-rejection prevention
        if (contract.getUser().getUsername().equals(username)) {
            throw new UnauthorizedException("Kendi sözleşmenizi reddederek iptal edemezsiniz; bunun yerine sözleşmeyi silin");
        }

        // Kimlik doğrulama gate — reddetmek için doğrulanmış olmalı
        User approver = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Approver not found: " + username));
        if (approver.getTcKimlik() == null) {
            throw new BadRequestException(
                    "Sözleşme reddetmek için önce Kimlik Doğrulama sayfasından kimliğinizi doğrulamanız gerekmektedir");
        }

        // D4: TC Kimlik counterparty validation
        if (contract.getCounterpartyTcKimlik() != null &&
                !approver.getTcKimlik().equals(contract.getCounterpartyTcKimlik())) {
            throw new UnauthorizedException("Bu sözleşmeyi reddetme yetkisine sahip değilsiniz");
        }

        contract.setStatus("REJECTED");
        contract.setUpdatedAt(OffsetDateTime.now());
        return convertToResponse(contractRepository.save(contract));
    }

    /** Sözleşme sahibi değilse 404 döner (kaynak maskeleme ile IDOR önleme) */
    private void verifyOwnership(Contract contract, String username) {
        if (!contract.getUser().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Contract not found with id: " + contract.getId());
        }
    }

    private ContractResponse convertToResponse(Contract contract) {
        return ContractResponse.builder()
                .id(contract.getId())
                .title(contract.getTitle())
                .type(contract.getType())
                .status(contract.getStatus())
                .content(contract.getContent())
                .amount(contract.getAmount())
                .counterpartyName(contract.getCounterpartyName())
                .counterpartyRole(contract.getCounterpartyRole())
                .counterpartyTcKimlik(contract.getCounterpartyTcKimlik())
                .userId(contract.getUser().getId())
                .ownerUsername(contract.getUser().getUsername())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
