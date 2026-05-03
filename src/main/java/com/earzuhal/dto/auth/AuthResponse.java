package com.earzuhal.dto.auth;

import com.earzuhal.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;

    /** Uzun ömürlü refresh token. Access token süresi dolduğunda yenilemek için kullanılır. */
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn;

    private UserResponse userInfo;

    /** Doğru olduğunda istemci 2FA kodunu sorup login isteğini code ile tekrarlamalı. */
    @Builder.Default
    private Boolean requires2fa = false;
}
