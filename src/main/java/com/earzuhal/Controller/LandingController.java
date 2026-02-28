package com.earzuhal.Controller;

import com.earzuhal.Service.LandingService;
import com.earzuhal.dto.landing.DemoRequestDto;
import com.earzuhal.dto.landing.LandingStatsResponse;
import com.earzuhal.dto.landing.NewsletterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/landing")
public class LandingController {

    private final LandingService landingService;

    public LandingController(LandingService landingService) {
        this.landingService = landingService;
    }

    @GetMapping("/stats")
    public ResponseEntity<LandingStatsResponse> getStats() {
        return ResponseEntity.ok(landingService.getStats());
    }

    @PostMapping("/demo-request")
    public ResponseEntity<Void> submitDemoRequest(@Valid @RequestBody DemoRequestDto dto) {
        landingService.submitDemoRequest(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/newsletter")
    public ResponseEntity<Void> subscribeNewsletter(@Valid @RequestBody NewsletterRequest request) {
        landingService.subscribeNewsletter(request);
        return ResponseEntity.ok().build();
    }
}
