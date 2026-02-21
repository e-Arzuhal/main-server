package com.earzuhal.Controller;

import com.earzuhal.Service.VerificationService;
import com.earzuhal.dto.verification.VerificationRequest;
import com.earzuhal.dto.verification.VerificationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Kimlik doğrulama isteği.
     * Body: { tcNo, firstName, lastName, dateOfBirth, method, mrzData? }
     */
    @PostMapping("/identity")
    public ResponseEntity<VerificationResponse> verify(
            @RequestBody VerificationRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(verificationService.verify(request, authentication.getName()));
    }

    /**
     * Giriş yapan kullanıcının doğrulama durumunu döner.
     */
    @GetMapping("/status")
    public ResponseEntity<VerificationResponse> getStatus(Authentication authentication) {
        return ResponseEntity.ok(verificationService.getStatus(authentication.getName()));
    }
}
