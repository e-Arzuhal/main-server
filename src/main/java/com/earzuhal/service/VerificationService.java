package com.earzuhal.service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.IdentityVerification;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.Repository.IdentityVerificationRepository;
import com.earzuhal.dto.verification.VerificationRequest;
import com.earzuhal.dto.verification.VerificationResponse;
import com.earzuhal.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final IdentityVerificationRepository verificationRepository;
    private final ContractRepository contractRepository;
    private final NotificationService notificationService;
    private final TcKimlikEncryptionService encryptionService;
    private final UserService userService;

    public VerificationService(IdentityVerificationRepository verificationRepository,
                               ContractRepository contractRepository,
                               NotificationService notificationService,
                               TcKimlikEncryptionService encryptionService,
                               UserService userService) {
        this.verificationRepository = verificationRepository;
        this.contractRepository = contractRepository;
        this.notificationService = notificationService;
        this.encryptionService = encryptionService;
        this.userService = userService;
    }

    @Transactional
    public VerificationResponse verify(VerificationRequest request, String username) {
        if (!isValidTcNo(request.getTcNo())) {
            throw new BadRequestException("Geçersiz TC Kimlik Numarası. Lütfen 11 haneli numaranızı kontrol edin.");
        }

        // TODO: Prodüksiyon için NVI (Nüfus ve Vatandaşlık İşleri) API entegrasyonu gereklidir.
        // Şu an sadece algoritma doğrulaması yapılmaktadır. Sahte TC ile doğrulama riski vardır.
        // NVI API veya e-Devlet doğrulama servisi entegre edildikten sonra bu uyarı kaldırılmalıdır.

        User user = userService.getUserByUsernameOrEmail(username);

        // Güvenlik: tcKimlik bir kez doğrulandıktan sonra değiştirilemez
        if (user.getTcKimlik() != null) {
            throw new BadRequestException("Kimlik doğrulaması zaten yapılmış. TC Kimlik numarası değiştirilemez.");
        }

        // Kayıttaki ad-soyad ile doğrulamada girilen ad-soyad birebir aynı olmalı.
        if (!normalizeName(user.getFirstName()).equals(normalizeName(request.getFirstName()))
                || !normalizeName(user.getLastName()).equals(normalizeName(request.getLastName()))) {
            throw new BadRequestException(
                    "Girilen ad ve soyad, hesabınıza kayıtlı ad-soyad ile eşleşmiyor. " +
                    "Lütfen kayıt sırasında kullandığınız ad ve soyadı girin.");
        }

        // Varsa mevcut kaydı güncelle, yoksa yeni oluştur
        Optional<IdentityVerification> existing = verificationRepository.findByUserId(user.getId());
        IdentityVerification verification = existing.orElse(new IdentityVerification());

        verification.setUser(user);
        verification.setTcNoMasked(maskTcNo(request.getTcNo()));
        verification.setFirstName(request.getFirstName());
        verification.setLastName(request.getLastName());
        verification.setDateOfBirth(request.getDateOfBirth());
        verification.setVerificationMethod(request.getMethod() != null ? request.getMethod().toUpperCase() : "MANUAL");
        verification.setStatus("VERIFIED");
        verification.setVerifiedAt(OffsetDateTime.now());
        if (verification.getCreatedAt() == null) {
            verification.setCreatedAt(OffsetDateTime.now());
        }

        verificationRepository.save(verification);

        // TC Kimlik'i şifreleyerek kaydet — plaintext asla DB'ye yazılmaz
        String encryptedTc = encryptionService.encrypt(request.getTcNo());
        user.setTcKimlik(encryptedTc);

        log.info("Identity verified for user={} method={}", username, verification.getVerificationMethod());

        // Kimlik doğrulandıktan sonra: bu TC ile bekleyen sözleşmeleri bildir.
        // Bu sayede karşı taraf uygulamaya sonradan kayıt olsa bile PENDING sözleşmelerini görür.
        notifyPendingContractsForNewUser(user, encryptedTc);

        return VerificationResponse.builder()
                .status("VERIFIED")
                .message("Kimlik doğrulaması başarıyla tamamlandı.")
                .tcNoMasked(verification.getTcNoMasked())
                .firstName(verification.getFirstName())
                .lastName(verification.getLastName())
                .verificationMethod(verification.getVerificationMethod())
                .verifiedAt(verification.getVerifiedAt())
                .verified(true)
                .build();
    }

    public VerificationResponse getStatus(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        Optional<IdentityVerification> opt = verificationRepository.findByUserId(user.getId());

        if (opt.isEmpty() || !"VERIFIED".equals(opt.get().getStatus())) {
            return VerificationResponse.builder()
                    .status("UNVERIFIED")
                    .message("Kimlik doğrulaması henüz yapılmamış.")
                    .verified(false)
                    .build();
        }

        IdentityVerification v = opt.get();
        return VerificationResponse.builder()
                .status("VERIFIED")
                .message("Kimlik doğrulandı.")
                .tcNoMasked(v.getTcNoMasked())
                .firstName(v.getFirstName())
                .lastName(v.getLastName())
                .verificationMethod(v.getVerificationMethod())
                .verifiedAt(v.getVerifiedAt())
                .verified(true)
                .build();
    }

    /**
     * Yeni kimlik doğrulaması yapan kullanıcıya, daha önce başkası tarafından
     * bu TC ile oluşturulmuş PENDING sözleşmeleri bildirir.
     *
     * Senaryo: Alice sözleşme oluşturdu, karşı taraf Bob'un TC'sini girdi.
     * Bob henüz kayıtlı değildi. Bob kayıt olup TC doğruladığında bekleyen
     * sözleşmelerini burada öğrenir.
     */
    private void notifyPendingContractsForNewUser(User user, String encryptedTc) {
        try {
            List<Contract> pending = contractRepository
                    .findByCounterpartyTcKimlikAndStatusOrderByCreatedAtDesc(encryptedTc, "PENDING");
            if (pending.isEmpty()) return;

            log.info("Yeni kimlik doğrulaması: user={} için {} PENDING sözleşme bulundu",
                    user.getUsername(), pending.size());

            for (Contract contract : pending) {
                notificationService.notifyUser(
                        user,
                        "CONTRACT_PENDING_APPROVAL",
                        "Onay bekleyen sözleşme",
                        "\"" + contract.getTitle() + "\" başlıklı sözleşme onayınızı bekliyor.",
                        contract.getId()
                );
            }
        } catch (Exception e) {
            // Bildirim hatası, ana akışı bozmamalı
            log.warn("Bekleyen sözleşme bildirimi gönderilemedi: user={} hata={}", user.getUsername(), e.getMessage());
        }
    }

    /**
     * Türkiye TC Kimlik Numarası doğrulama algoritması.
     * - 11 hane, tamamı rakam
     * - İlk hane 0 olamaz
     * - 10. hane: (7*(d1+d3+d5+d7+d9) − (d2+d4+d6+d8)) mod 10
     * - 11. hane: (d1+d2+…+d10) mod 10
     */
    private boolean isValidTcNo(String tcNo) {
        if (tcNo == null || !tcNo.matches("\\d{11}")) return false;
        if (tcNo.charAt(0) == '0') return false;

        int[] d = new int[11];
        for (int i = 0; i < 11; i++) d[i] = tcNo.charAt(i) - '0';

        int d10 = (7 * (d[0] + d[2] + d[4] + d[6] + d[8]) - (d[1] + d[3] + d[5] + d[7])) % 10;
        if (d10 < 0) d10 += 10;
        if (d[9] != d10) return false;

        int sum = 0;
        for (int i = 0; i < 10; i++) sum += d[i];
        return d[10] == sum % 10;
    }

    /** "12345678901" → "123******01" */
    private String maskTcNo(String tcNo) {
        return tcNo.substring(0, 3) + "******" + tcNo.substring(9);
    }

    /**
     * Türkçe ad/soyad eşitlik karşılaştırması için normalleştirme:
     * trim, küçült, çoklu boşluğu sadeleştir, Türkçe karakter farklarını kaldır.
     */
    private static String normalizeName(String s) {
        if (s == null) return "";
        String lower = s.trim().toLowerCase(java.util.Locale.forLanguageTag("tr"))
                .replaceAll("\\s+", " ");
        return lower
                .replace('ı', 'i').replace('İ', 'i')
                .replace('ş', 's').replace('Ş', 's')
                .replace('ç', 'c').replace('Ç', 'c')
                .replace('ğ', 'g').replace('Ğ', 'g')
                .replace('ö', 'o').replace('Ö', 'o')
                .replace('ü', 'u').replace('Ü', 'u');
    }
}
