package com.earzuhal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * E-posta gönderimi için ince bir sarmalayıcı.
 * mail.enabled=false ise mesajları sadece log'a yazar (dev ortamı için).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${mail.from:noreply@earzuhal.com}")
    private String fromAddress;

    @Autowired(required = false)
    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;

        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL devre dışı] to={} subject={} body={}", to, subject, body);
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Mail gönderildi to={} subject={}", to, subject);
        } catch (Exception ex) {
            log.warn("Mail gönderilemedi to={} subject={} err={}", to, subject, ex.getMessage());
        }
    }
}
