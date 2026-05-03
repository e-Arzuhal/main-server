package com.earzuhal.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /**
     * 2FA etkinse istemci ilk login isteğine null yollar; sunucu kod gönderir
     * ve {@code requires2fa=true} döner. İstemci aynı login'i bu kez bu alanı
     * doldurarak tekrar yollar.
     */
    private String twoFactorCode;

    /** Geriye dönük uyum: 2FA kodu olmadan oluşturulan eski çağrılar için. */
    public LoginRequest(String usernameOrEmail, String password) {
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
        this.twoFactorCode = null;
    }
}
