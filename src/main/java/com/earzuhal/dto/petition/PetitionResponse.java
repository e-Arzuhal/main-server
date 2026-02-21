package com.earzuhal.dto.petition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetitionResponse {

    private Long id;
    private String kurum;
    private String kurumAdresi;
    private String yetkili;
    private String konu;
    private String govde;
    private String status;
    private Long userId;
    private String ownerUsername;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
