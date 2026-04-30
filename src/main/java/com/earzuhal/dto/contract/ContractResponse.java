package com.earzuhal.dto.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractResponse {

    private Long id;
    private String title;
    private String type;
    private String status;
    private String content;
    private String amount;
    private String counterpartyName;
    private String counterpartyRole;
    private String counterpartyTcKimlik;
    private Long userId;
    private String ownerUsername;
    /** Sözleşme sahibinin görüntülenebilir tam adı (firstName + lastName). Boşsa username fallback. */
    private String ownerFullName;
    private Boolean viewerIsOwner;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
