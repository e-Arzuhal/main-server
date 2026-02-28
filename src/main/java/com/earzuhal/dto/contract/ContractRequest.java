package com.earzuhal.dto.contract;

import com.earzuhal.dto.explanation.AnalysisContextDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 50, message = "Type must not exceed 50 characters")
    private String type;

    private String content;

    @Size(max = 100, message = "Amount must not exceed 100 characters")
    private String amount;

    @Size(max = 100, message = "Counterparty name must not exceed 100 characters")
    private String counterpartyName;

    @Size(max = 50, message = "Counterparty role must not exceed 50 characters")
    private String counterpartyRole;

    /**
     * Opsiyonel analiz bağlamı. Frontend analiz sonrasında sözleşme oluştururken
     * bu alanı doldurursa madde açıklamaları otomatik üretilir.
     */
    private AnalysisContextDto analysisContext;
}
