package com.earzuhal.dto.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeRequest {

    @NotBlank(message = "Text is required")
    @Size(min = 10, max = 5000)
    private String text;
}
