package com.earzuhal.Service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.dto.contract.ContractRequest;
import com.earzuhal.dto.contract.ContractResponse;
import com.earzuhal.dto.contract.ContractStatsResponse;
import com.earzuhal.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final UserService userService;

    public ContractService(ContractRepository contractRepository, UserService userService) {
        this.contractRepository = contractRepository;
        this.userService = userService;
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

    public ContractResponse getById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        return convertToResponse(contract);
    }

    @Transactional
    public ContractResponse update(Long id, ContractRequest request) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));

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
    public void delete(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        contractRepository.delete(contract);
    }

    @Transactional
    public ContractResponse finalize(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
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
    public ContractResponse approve(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        contract.setStatus("APPROVED");
        contract.setUpdatedAt(OffsetDateTime.now());
        return convertToResponse(contractRepository.save(contract));
    }

    @Transactional
    public ContractResponse reject(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
        contract.setStatus("REJECTED");
        contract.setUpdatedAt(OffsetDateTime.now());
        return convertToResponse(contractRepository.save(contract));
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
