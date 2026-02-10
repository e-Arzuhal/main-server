package com.earzuhal.Controller;

import com.earzuhal.Service.ContractService;
import com.earzuhal.dto.contract.ContractRequest;
import com.earzuhal.dto.contract.ContractResponse;
import com.earzuhal.dto.contract.ContractStatsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping
    public ResponseEntity<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
        String username = getCurrentUsername();
        ContractResponse response = contractService.create(request, username);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ContractResponse>> getAll() {
        String username = getCurrentUsername();
        List<ContractResponse> contracts = contractService.getAllByUser(username);
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractResponse> getById(@PathVariable Long id) {
        ContractResponse contract = contractService.getById(id);
        return ResponseEntity.ok(contract);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody ContractRequest request) {
        ContractResponse contract = contractService.update(id, request);
        return ResponseEntity.ok(contract);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<ContractResponse> finalize(@PathVariable Long id) {
        ContractResponse contract = contractService.finalize(id);
        return ResponseEntity.ok(contract);
    }

    @GetMapping("/pending-approval")
    public ResponseEntity<List<ContractResponse>> getPendingApprovals() {
        String username = getCurrentUsername();
        List<ContractResponse> contracts = contractService.getPendingApprovals(username);
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/stats")
    public ResponseEntity<ContractStatsResponse> getStats() {
        String username = getCurrentUsername();
        ContractStatsResponse stats = contractService.getStats(username);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ContractResponse> approve(@PathVariable Long id) {
        ContractResponse contract = contractService.approve(id);
        return ResponseEntity.ok(contract);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ContractResponse> reject(@PathVariable Long id) {
        ContractResponse contract = contractService.reject(id);
        return ResponseEntity.ok(contract);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
