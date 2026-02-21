package com.earzuhal.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    /** 11 haneli TC Kimlik Numarası */
    private String tcNo;

    private String firstName;

    private String lastName;

    private LocalDate dateOfBirth;

    /** NFC | MRZ | MANUAL */
    private String method;

    /** Opsiyonel — NFC/kamera ile okunan ham MRZ verisi */
    private String mrzData;
}
