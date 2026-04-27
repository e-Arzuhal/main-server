package com.earzuhal.controller;

import com.earzuhal.service.AnalysisService;
import com.earzuhal.dto.analysis.AnalyzeRequest;
import com.earzuhal.dto.analysis.FullAnalysisResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Kullanıcının girdiği metni analiz eder.
     * NLP + GraphRAG pipeline'ını orchestrate eder.
     *
     * POST /api/analysis/analyze
     * { "text": "Ahmet ile Mehmet arasında 5000 TL borç sözleşmesi..." }
     */
    @PostMapping("/analyze")
    public ResponseEntity<FullAnalysisResponse> analyze(@Valid @RequestBody AnalyzeRequest request) {
        FullAnalysisResponse result = analysisService.analyzeText(request.getText());
        return ResponseEntity.ok(result);
    }
}
