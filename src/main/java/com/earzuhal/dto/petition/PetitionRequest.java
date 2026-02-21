package com.earzuhal.dto.petition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetitionRequest {

    @NotBlank(message = "Kurum adı zorunludur")
    @Size(max = 200, message = "Kurum adı 200 karakteri geçemez")
    private String kurum;

    @Size(max = 300, message = "Kurum adresi 300 karakteri geçemez")
    private String kurumAdresi;

    @Size(max = 150, message = "Yetkili alanı 150 karakteri geçemez")
    private String yetkili;

    @NotBlank(message = "Konu zorunludur")
    @Size(max = 300, message = "Konu 300 karakteri geçemez")
    private String konu;

    @NotBlank(message = "Dilekçe içeriği zorunludur")
    private String govde;
}
