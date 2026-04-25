package com.earzuhal.dto.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    /** 11 haneli TC Kimlik Numarası */
    @NotBlank(message = "TC Kimlik Numarası zorunludur")
    @Size(min = 11, max = 11, message = "TC Kimlik Numarası 11 haneli olmalıdır")
    @Pattern(regexp = "^[1-9]\\d{10}$", message = "Geçersiz TC Kimlik Numarası formatı")
    private String tcNo;

    @NotBlank(message = "Ad zorunludur")
    @Size(max = 50, message = "Ad 50 karakteri geçemez")
    private String firstName;

    @NotBlank(message = "Soyad zorunludur")
    @Size(max = 50, message = "Soyad 50 karakteri geçemez")
    private String lastName;

    @NotNull(message = "Doğum tarihi zorunludur")
    private LocalDate dateOfBirth;

    /** NFC | MRZ | MANUAL */
    @NotBlank(message = "Doğrulama yöntemi zorunludur")
    @Pattern(regexp = "^(NFC|MRZ|MANUAL)$", message = "Geçersiz yöntem. Geçerli değerler: NFC, MRZ, MANUAL")
    private String method;

    /** Opsiyonel — NFC/kamera ile okunan ham MRZ verisi */
    private String mrzData;
}
