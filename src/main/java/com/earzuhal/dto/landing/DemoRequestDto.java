package com.earzuhal.dto.landing;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoRequestDto {

    @NotBlank(message = "Ad Soyad boş olamaz")
    @Size(max = 100, message = "Ad Soyad 100 karakteri geçemez")
    private String fullName;

    @NotBlank(message = "E-posta boş olamaz")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    @Size(max = 150, message = "E-posta 150 karakteri geçemez")
    private String email;

    @Size(max = 150, message = "Şirket adı 150 karakteri geçemez")
    private String company;

    @Size(max = 30, message = "Telefon 30 karakteri geçemez")
    private String phone;
}
