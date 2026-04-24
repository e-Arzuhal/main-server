package com.earzuhal.controller;

import com.earzuhal.Controller.VerificationController;
import com.earzuhal.Service.VerificationService;
import com.earzuhal.dto.verification.VerificationRequest;
import com.earzuhal.dto.verification.VerificationResponse;
import com.earzuhal.exception.BadRequestException;
import com.earzuhal.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verification Controller entegrasyon testi.
 *
 * Jüri kanıtları:
 *  ✓ Geçerli TC ile kimlik doğrulama başarılı
 *  ✓ Geçersiz TC formatı 400 döndürüyor (input validation)
 *  ✓ Zorunlu alanlar boş bırakıldığında 400 döndürüyor
 *  ✓ Durum sorgusu 200 döndürüyor
 */
@WebMvcTest(controllers = VerificationController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class VerificationControllerIT {

    @Autowired MockMvc mvc;

    @MockitoBean VerificationService verificationService;

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    // ── Başarılı doğrulama ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("200 — geçerli TC Kimlik ile doğrulama başarılı")
    void verify_with_valid_tc_returns_200() throws Exception {
        VerificationRequest req = new VerificationRequest();
        req.setTcNo("10000000146");
        req.setFirstName("Ali");
        req.setLastName("Yılmaz");
        req.setDateOfBirth(LocalDate.of(1990, 1, 15));
        req.setMethod("MANUAL");

        VerificationResponse resp = new VerificationResponse();
        resp.setVerified(true);
        resp.setMessage("Kimlik doğrulaması başarılı.");

        when(verificationService.verify(any(), eq("alice"))).thenReturn(resp);

        mvc.perform(post("/api/verification/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    // ── @Valid doğrulama: geçersiz TC formatı ───────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("400 — geçersiz TC formatı (11 haneden kısa) reddediliyor")
    void verify_with_short_tc_returns_400() throws Exception {
        VerificationRequest req = new VerificationRequest();
        req.setTcNo("12345");               // 11 haneden kısa
        req.setFirstName("Ali");
        req.setLastName("Yılmaz");
        req.setDateOfBirth(LocalDate.of(1990, 1, 15));
        req.setMethod("MANUAL");

        mvc.perform(post("/api/verification/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("400 — TC 0 ile başlıyor → regex pattern reddediyor")
    void verify_with_zero_starting_tc_returns_400() throws Exception {
        VerificationRequest req = new VerificationRequest();
        req.setTcNo("01234567890");         // 0 ile başlıyor
        req.setFirstName("Ali");
        req.setLastName("Yılmaz");
        req.setDateOfBirth(LocalDate.of(1990, 1, 15));
        req.setMethod("MANUAL");

        mvc.perform(post("/api/verification/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── @Valid doğrulama: zorunlu alanlar ────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("400 — zorunlu alanlar eksikken request reddediliyor")
    void verify_with_missing_required_fields_returns_400() throws Exception {
        // Tüm alanlar null/boş
        VerificationRequest req = new VerificationRequest();

        mvc.perform(post("/api/verification/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("400 — geçersiz method değeri (NFC/MRZ/MANUAL dışı) reddediliyor")
    void verify_with_invalid_method_returns_400() throws Exception {
        VerificationRequest req = new VerificationRequest();
        req.setTcNo("10000000146");
        req.setFirstName("Ali");
        req.setLastName("Yılmaz");
        req.setDateOfBirth(LocalDate.of(1990, 1, 15));
        req.setMethod("INVALID_METHOD");    // Geçersiz yöntem

        mvc.perform(post("/api/verification/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── Durum sorgulama ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("200 — doğrulama durumu başarıyla dönüyor")
    void get_status_returns_200() throws Exception {
        VerificationResponse resp = new VerificationResponse();
        resp.setVerified(false);
        resp.setMessage("Henüz doğrulama yapılmamış.");

        when(verificationService.getStatus("alice")).thenReturn(resp);

        mvc.perform(get("/api/verification/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
    }
}
