package com.earzuhal.dto.disclaimer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisclaimerAcceptRequest {

    /** Kabul yapılan platform: WEB veya MOBILE */
    private String platform;
}
