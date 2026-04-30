package com.earzuhal.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    /**
     * Eski alan: hâlâ kabul edilir (sadece kod). Yeni akışta {@link #email} ve {@link #code} kullanılır.
     */
    private String token;

    @Email
    private String email;

    /** 6 haneli sıfırlama kodu (e-posta ile gönderilir). */
    private String code;

    @NotBlank
    @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
    private String newPassword;
}
