package com.earzuhal.controller;

import com.earzuhal.Model.Petition;
import com.earzuhal.service.PdfService;
import com.earzuhal.service.PetitionService;
import com.earzuhal.dto.petition.PetitionRequest;
import com.earzuhal.dto.petition.PetitionResponse;
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
@RequestMapping("/api/petitions")
public class PetitionController {

    private final PetitionService petitionService;
    private final PdfService pdfService;

    public PetitionController(PetitionService petitionService, PdfService pdfService) {
        this.petitionService = petitionService;
        this.pdfService = pdfService;
    }

    @PostMapping
    public ResponseEntity<PetitionResponse> create(@Valid @RequestBody PetitionRequest request) {
        PetitionResponse response = petitionService.create(request, currentUsername());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<PetitionResponse>> getAll() {
        return ResponseEntity.ok(petitionService.getAllByUser(currentUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PetitionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(petitionService.getById(id, currentUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PetitionResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody PetitionRequest request) {
        return ResponseEntity.ok(petitionService.update(id, request, currentUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        petitionService.delete(id, currentUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<PetitionResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(petitionService.complete(id, currentUsername()));
    }

    /**
     * PDF'deki SHA-256 parmak izini DB'den yeniden hesaplayıp karşılaştırır.
     * GET /api/petitions/{id}/verify?hash=<sha256>
     */
    @GetMapping("/{id}/verify")
    public ResponseEntity<java.util.Map<String, Object>> verifyDocumentHash(
            @PathVariable Long id,
            @RequestParam String hash) {
        com.earzuhal.Model.Petition petition = petitionService.getEntityById(id, currentUsername());
        String expected = pdfService.computePetitionHash(petition);
        boolean valid = expected.equalsIgnoreCase(hash);
        return ResponseEntity.ok(java.util.Map.of(
                "valid", valid,
                "petitionId", id,
                "message", valid ? "Belge bütünlüğü doğrulandı." : "Hash uyuşmuyor — belge değiştirilmiş olabilir."
        ));
    }

    /** Dilekçeyi PDF olarak indir */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Petition petition = petitionService.getEntityById(id, currentUsername());
        byte[] pdf = pdfService.generatePetitionPdf(petition);

        String filename = String.format("dilekce-%06d.pdf", id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdf.length);

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
