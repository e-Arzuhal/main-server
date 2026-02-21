package com.earzuhal.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {

    /** VERIFIED | UNVERIFIED | FAILED */
    private String status;

    private String message;

    private String tcNoMasked;

    private String firstName;

    private String lastName;

    /** NFC | MRZ | MANUAL */
    private String verificationMethod;

    private OffsetDateTime verifiedAt;

    private boolean verified;
}
