package com.earzuhal.dto.disclaimer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisclaimerStatusResponse {

    private boolean accepted;
    private String version;
    private OffsetDateTime acceptedAt;
}
