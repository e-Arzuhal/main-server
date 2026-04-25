package com.earzuhal.Controller;

import com.earzuhal.Model.Contract;
import com.earzuhal.Service.ContractService;
import com.earzuhal.Service.PdfService;
import com.earzuhal.dto.contract.ContractRequest;
import com.earzuhal.dto.contract.ContractResponse;
import com.earzuhal.dto.contract.ContractStatsResponse;
import com.earzuhal.dto.contract.PdfConfirmResponse;
import com.earzuhal.dto.explanation.ContractExplanationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;
    private final PdfService pdfService;

    public ContractController(ContractService contractService, PdfService pdfService) {
        this.contractService = contractService;
        this.pdfService = pdfService;
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
        ContractResponse contract = contractService.getById(id, getCurrentUsername());
        return ResponseEntity.ok(contract);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody ContractRequest request) {
        ContractResponse contract = contractService.update(id, request, getCurrentUsername());
        return ResponseEntity.ok(contract);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contractService.delete(id, getCurrentUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<ContractResponse> finalize(@PathVariable Long id) {
        ContractResponse contract = contractService.finalize(id, getCurrentUsername());
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
        ContractResponse contract = contractService.approve(id, getCurrentUsername());
        return ResponseEntity.ok(contract);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ContractResponse> reject(@PathVariable Long id) {
        ContractResponse contract = contractService.reject(id, getCurrentUsername());
        return ResponseEntity.ok(contract);
    }

    /**
     * Sözleşmedeki her maddenin neden eklendiğini, hangi kanun maddesine
     * dayandığını ve istatistiksel yaygınlığını açıklar.
     * GET /api/contracts/{id}/explanation
     */
    @GetMapping("/{id}/explanation")
    public ResponseEntity<ContractExplanationResponse> getExplanation(@PathVariable Long id) {
        ContractExplanationResponse explanation =
                contractService.getExplanation(id, getCurrentUsername());
        return ResponseEntity.ok(explanation);
    }

    /**
     * PDF oluşturmadan önce kullanıcıya onay verisi sunar.
     * NLP'nin parse ettiği tutar, taraflar vb. doğrulanabilsin diye.
     * Frontend bu yanıtı onay dialogunda gösterir; readyForPdf=false ise uyarı gösterilmeli.
     * GET /api/contracts/{id}/pdf-confirm
     */
    @GetMapping("/{id}/pdf-confirm")
    public ResponseEntity<PdfConfirmResponse> getPdfConfirmData(@PathVariable Long id) {
        PdfConfirmResponse confirm = contractService.getPdfConfirmData(id, getCurrentUsername());
        return ResponseEntity.ok(confirm);
    }

    /**
     * PDF'deki SHA-256 parmak izini DB'den yeniden hesaplayıp karşılaştırır.
     * GET /api/contracts/{id}/verify?hash=<sha256>
     * Belgeyi elinde bulunduran herhangi bir taraf (sahip ya da karşı taraf değil,
     * mevcut akışta yalnızca sahip) doğrulama yapabilir.
     */
    @GetMapping("/{id}/verify")
    public ResponseEntity<java.util.Map<String, Object>> verifyDocumentHash(
            @PathVariable Long id,
            @RequestParam String hash) {
        com.earzuhal.Model.Contract contract = contractService.getEntityById(id, getCurrentUsername());
        String expected = pdfService.computeContractHash(contract);
        boolean valid = expected.equalsIgnoreCase(hash);
        return ResponseEntity.ok(java.util.Map.of(
                "valid", valid,
                "contractId", id,
                "message", valid ? "Belge bütünlüğü doğrulandı." : "Hash uyuşmuyor — belge değiştirilmiş olabilir."
        ));
    }

    /** Sözleşmeyi PDF olarak indir */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Contract contract = contractService.getEntityById(id, getCurrentUsername());
        byte[] pdf = pdfService.generateContractPdf(contract);

        String filename = String.format("sozlesme-%06d.pdf", id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdf.length);

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
