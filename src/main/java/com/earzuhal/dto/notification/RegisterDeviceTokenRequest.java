package com.earzuhal.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "Push token boş olamaz")
    @Size(max = 255, message = "Push token çok uzun")
    private String token;

    @Size(max = 20, message = "Platform alanı çok uzun")
    private String platform;
}
