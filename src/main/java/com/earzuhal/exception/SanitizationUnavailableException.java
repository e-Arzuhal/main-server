package com.earzuhal.exception;

/**
 * NLP sanitization servisi erişilemez olduğunda fırlatılır. Bu hata fallback ile
 * yutulmaz: arındırılmamış metnin Gemini gibi dış LLM servislerine sızmasını
 * önler. Kullanıcıya 503 Service Unavailable döner.
 */
public class SanitizationUnavailableException extends RuntimeException {
    public SanitizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
