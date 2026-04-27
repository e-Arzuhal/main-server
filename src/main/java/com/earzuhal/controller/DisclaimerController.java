package com.earzuhal.controller;

import com.earzuhal.service.DisclaimerService;
import com.earzuhal.dto.disclaimer.DisclaimerAcceptRequest;
import com.earzuhal.dto.disclaimer.DisclaimerStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/disclaimer")
public class DisclaimerController {

    private final DisclaimerService disclaimerService;

    public DisclaimerController(DisclaimerService disclaimerService) {
        this.disclaimerService = disclaimerService;
    }

    /** Kullanıcının mevcut yasal uyarı versiyonunu kabul ettiğini kaydeder */
    @PostMapping("/accept")
    public ResponseEntity<DisclaimerStatusResponse> accept(@RequestBody DisclaimerAcceptRequest request) {
        return ResponseEntity.ok(disclaimerService.accept(getCurrentUsername(), request.getPlatform()));
    }

    /** Kullanıcının güncel versiyonu kabul edip etmediğini döner */
    @GetMapping("/status")
    public ResponseEntity<DisclaimerStatusResponse> status() {
        return ResponseEntity.ok(disclaimerService.getStatus(getCurrentUsername()));
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
}
