package com.earzuhal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TC Kimlik AES-256/ECB encryption.
 * No Spring context needed — pure unit test.
 */
class TcKimlikEncryptionServiceTest {

    // 32-byte key encoded as Base64 (test-only, never use in production)
    private static final String TEST_KEY_B64 =
            Base64.getEncoder().encodeToString(new byte[32]); // 32 zero bytes

    private TcKimlikEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new TcKimlikEncryptionService(TEST_KEY_B64);
    }

    @Test
    @DisplayName("Encrypt → decrypt round-trip returns original plaintext")
    void roundTrip() {
        String tc = "12345678901";
        String encrypted = service.encrypt(tc);
        String decrypted = service.decrypt(encrypted);
        assertEquals(tc, decrypted);
    }

    @Test
    @DisplayName("Encryption is deterministic: same input → same ciphertext")
    void deterministic() {
        String tc = "98765432100";
        String first = service.encrypt(tc);
        String second = service.encrypt(tc);
        assertEquals(first, second,
                "AES/ECB must be deterministic so DB equality queries work");
    }

    @Test
    @DisplayName("Different TC values produce different ciphertexts")
    void differentInputsDifferentCiphertexts() {
        String enc1 = service.encrypt("11111111110");
        String enc2 = service.encrypt("22222222220");
        assertNotEquals(enc1, enc2);
    }

    @Test
    @DisplayName("Null input encrypts to null (users without TC)")
    void nullInputReturnsNull() {
        assertNull(service.encrypt(null));
        assertNull(service.decrypt(null));
    }

    @Test
    @DisplayName("Blank input encrypts to null")
    void blankInputReturnsNull() {
        assertNull(service.encrypt("   "));
    }

    @Test
    @DisplayName("decryptAndMask returns 123******01 format")
    void maskFormat() {
        String tc = "12345678901";
        String encrypted = service.encrypt(tc);
        String masked = service.decryptAndMask(encrypted);
        assertEquals("123******01", masked);
        // Verify pattern: first 3 + 6 stars + last 2 characters
        assertTrue(masked.matches("\\d{3}\\*{6}\\d{2}"),
                "Mask must match \\d{3}\\*{6}\\d{2}");
    }

    @Test
    @DisplayName("decryptAndMask on null returns null")
    void maskNullReturnsNull() {
        assertNull(service.decryptAndMask(null));
    }

    @Test
    @DisplayName("Invalid Base64 key length throws IllegalStateException at construction")
    void invalidKeyLengthThrows() {
        // 16-byte key is valid AES-128 but we require 256 (32 bytes)
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class,
                () -> new TcKimlikEncryptionService(shortKey));
    }
}
