package com.earzuhal.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * TC Kimlik numaralarını AES-256/ECB ile şifreler.
 *
 * Neden ECB (deterministic)?
 *   DB'de equality query yapabilmek için aynı plaintext her zaman aynı ciphertext'i
 *   üretmeli. findByTcKimlik(), findByCounterpartyTcKimlik() şifrelenmiş değeri
 *   DB'deki şifrelenmiş değerle karşılaştırır — bu ancak deterministic encryption ile çalışır.
 *
 * Güvenlik notu — ECB neden burada kabul edilebilir?
 *   ECB'nin bilinen zafiyeti, aynı 16-byte blokların aynı ciphertext'e dönüşmesidir;
 *   bu durum tekrar eden bloklardan oluşan uzun veride görsel örüntü sızdırır.
 *   TC Kimlik 11 ASCII karakterdir — PKCS5 padding sonrası tam 16 byte (tek blok).
 *   Tek bloklu plaintext'te birden fazla aynı blok oluşması mümkün değildir;
 *   dolayısıyla ECB'nin pattern analizi zafiyeti bu input boyutunda tetiklenemez.
 *   Key environment variable'dan alınır, plaintext DB'ye yazılmaz.
 *
 * Anahtar format: 32-byte değerin Base64 encoding'i (openssl rand -base64 32 ile üret).
 */
@Service
public class TcKimlikEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private final SecretKeySpec secretKey;

    public TcKimlikEncryptionService(
            @Value("${tc.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "TC_ENCRYPTION_KEY must be a Base64-encoded 32-byte (256-bit) key. " +
                    "Generate with: openssl rand -base64 32"
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Plaintext TC Kimlik → Base64 AES ciphertext.
     * Null/blank input → null döner (DB'de null TC olan kullanıcılar için).
     */
    public String encrypt(String plainTcKimlik) {
        if (plainTcKimlik == null || plainTcKimlik.isBlank()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainTcKimlik.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("TC Kimlik şifrelenemedi", e);
        }
    }

    /**
     * Base64 AES ciphertext → plaintext TC Kimlik.
     * Null input → null döner.
     */
    public String decrypt(String encryptedTcKimlik) {
        if (encryptedTcKimlik == null || encryptedTcKimlik.isBlank()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.getDecoder().decode(encryptedTcKimlik);
            return new String(cipher.doFinal(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("TC Kimlik çözülemedi", e);
        }
    }

    /**
     * Plaintext TC Kimlik'i decrypt edip maskeler: "12345678901" → "123******01"
     * UI'da göstermek için.
     */
    public String decryptAndMask(String encryptedTcKimlik) {
        String plain = decrypt(encryptedTcKimlik);
        if (plain == null) return null;
        return plain.substring(0, 3) + "******" + plain.substring(9);
    }
}
