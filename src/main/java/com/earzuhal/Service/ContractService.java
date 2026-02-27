package com.earzuhal.Service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.dto.contract.ContractRequest;
import com.earzuhal.dto.contract.ContractResponse;
import com.earzuhal.dto.contract.ContractStatsResponse;
import com.earzuhal.exception.BadRequestException;
import com.earzuhal.exception.ResourceNotFoundException;
import com.earzuhal.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final UserService userService;
    private final DisclaimerService disclaimerService;

    public ContractService(ContractRepository contractRepository, UserService userService,
                           DisclaimerService disclaimerService) {
        this.contractRepository = contractRepository;
        this.userService = userService;
        this.disclaimerService = disclaimerService;
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
        contract.setStatus("DRAFT");
        contract.setUser(user);
        contract.setCreatedAt(OffsetDateTime.now());
        contract.setUpdatedAt(OffsetDateTime.now());

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
        return contractRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING")
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
        if (contract.getUser().getUsername().equals(username)) {
            throw new UnauthorizedException("Kendi sözleşmenizi onaylayamazsınız");
        }
        contract.setStatus("APPROVED");
        contract.setUpdatedAt(OffsetDateTime.now());
        return convertToResponse(contractRepository.save(contract));
    }

    @Transactional
    public ContractResponse reject(Long id, String username) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        if (contract.getUser().getUsername().equals(username)) {
            throw new UnauthorizedException("Kendi sözleşmenizi reddederek iptal edemezsiniz; bunun yerine sözleşmeyi silin");
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
                .userId(contract.getUser().getId())
                .ownerUsername(contract.getUser().getUsername())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
