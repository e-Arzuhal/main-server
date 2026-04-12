package com.earzuhal.Controller;

import com.earzuhal.Model.Petition;
import com.earzuhal.Service.PdfService;
import com.earzuhal.Service.PetitionService;
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
