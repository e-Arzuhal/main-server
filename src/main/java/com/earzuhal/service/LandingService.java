package com.earzuhal.service;

import com.earzuhal.Model.DemoRequest;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.Repository.DemoRequestRepository;
import com.earzuhal.Repository.UserRepository;
import com.earzuhal.dto.landing.DemoRequestDto;
import com.earzuhal.dto.landing.LandingStatsResponse;
import com.earzuhal.dto.landing.NewsletterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LandingService {

    private static final Logger log = LoggerFactory.getLogger(LandingService.class);

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final DemoRequestRepository demoRequestRepository;
    private final MailService mailService;

    @org.springframework.beans.factory.annotation.Value("${mail.demo.notify:}")
    private String demoNotifyAddress;

    public LandingService(UserRepository userRepository,
                          ContractRepository contractRepository,
                          DemoRequestRepository demoRequestRepository,
                          MailService mailService) {
        this.userRepository = userRepository;
        this.contractRepository = contractRepository;
        this.demoRequestRepository = demoRequestRepository;
        this.mailService = mailService;
    }

    public LandingStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalContracts = contractRepository.count();
        return LandingStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalContracts(totalContracts)
                .uptimePercent(99.9)
                .build();
    }

    @Transactional
    public void submitDemoRequest(DemoRequestDto dto) {
        DemoRequest entity = new DemoRequest();
        entity.setFullName(dto.getFullName());
        entity.setEmail(dto.getEmail());
        entity.setCompany(dto.getCompany());
        entity.setPhone(dto.getPhone());
        demoRequestRepository.save(entity);
        log.info("Demo talebi alındı: {} <{}>", dto.getFullName(), dto.getEmail());

        // 1) Talep sahibine onay e-postası
        String userBody = "Sayın " + safe(dto.getFullName()) + ",\n\n"
                + "e-Arzuhal demo talebiniz alındı. Ekibimiz en kısa sürede sizinle iletişime geçecek.\n\n"
                + "Bilgileriniz:\n"
                + "Ad Soyad: " + safe(dto.getFullName()) + "\n"
                + (dto.getCompany() != null && !dto.getCompany().isBlank() ? "Şirket: " + dto.getCompany() + "\n" : "")
                + (dto.getPhone() != null && !dto.getPhone().isBlank() ? "Telefon: " + dto.getPhone() + "\n" : "")
                + "\nSorularınız için bu e-postayı yanıtlayabilirsiniz.\n\nSaygılarımızla,\ne-Arzuhal Ekibi";
        mailService.send(dto.getEmail(), "e-Arzuhal Demo Talebiniz Alındı", userBody);

        // 2) Operasyon ekibine bilgilendirme (opsiyonel — mail.demo.notify tanımlıysa)
        if (demoNotifyAddress != null && !demoNotifyAddress.isBlank()) {
            String opsBody = "Yeni demo talebi:\n\n"
                    + "Ad Soyad: " + safe(dto.getFullName()) + "\n"
                    + "E-posta: " + safe(dto.getEmail()) + "\n"
                    + "Şirket: " + safe(dto.getCompany()) + "\n"
                    + "Telefon: " + safe(dto.getPhone()) + "\n";
            mailService.send(demoNotifyAddress, "Yeni Demo Talebi — " + safe(dto.getFullName()), opsBody);
        }
    }

    public void subscribeNewsletter(NewsletterRequest request) {
        log.info("Bülten aboneliği: {}", request.getEmail());
        if (request.getEmail() == null || request.getEmail().isBlank()) return;
        String body = "Merhaba,\n\n"
                + "e-Arzuhal bültenine başarıyla abone oldunuz. Bundan sonra yeni özellikler, hukuk ipuçları ve "
                + "duyurular bu adrese düzenli olarak iletilecek.\n\n"
                + "Aboneliği iptal etmek için bu e-postayı yanıtlayabilirsiniz.\n\nSaygılarımızla,\ne-Arzuhal Ekibi";
        mailService.send(request.getEmail(), "e-Arzuhal Bülten Aboneliğiniz Onaylandı", body);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
