package com.earzuhal.service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.IdentityVerification;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.Repository.IdentityVerificationRepository;
import com.earzuhal.Repository.UserRepository;
import com.earzuhal.exception.BadRequestException;
import com.earzuhal.exception.ResourceNotFoundException;
import com.earzuhal.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Merkezi yetkilendirme kontrolünü kanıtlayan güvenlik testleri.
 *
 * Jüri kanıtı: başka kullanıcının kaydına erişim → 404 (kaynak maskeleme ile IDOR önleme).
 * Self-approval → 403 (UnauthorizedException).
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceSecurityTest {

    @Mock ContractRepository contractRepository;
    @Mock UserRepository userRepository;
    @Mock IdentityVerificationRepository verificationRepository;
    @Mock UserService userService;
    @Mock DisclaimerService disclaimerService;
    @Mock ExplanationService explanationService;
    @Mock NotificationService notificationService;
    @Mock TcKimlikEncryptionService encryptionService;
    @Mock StatisticsService statisticsService;

    private ContractService contractService;

    @BeforeEach
    void setUp() {
        contractService = new ContractService(
                contractRepository, userRepository, verificationRepository,
                userService, disclaimerService, explanationService,
                notificationService, encryptionService, statisticsService, new ObjectMapper()
        );
    }

    // ── IDOR: başka kullanıcının sözleşmesini okuma ──────────────────────────

    @Test
    @DisplayName("IDOR — başka kullanıcının sözleşmesine getById isteği 404 döner")
    void getById_otherUsersContract_throwsNotFound() {
        User alice = user("alice");
        Contract alicesContract = contract(1L, alice);

        when(contractRepository.findById(1L)).thenReturn(Optional.of(alicesContract));

        assertThrows(
                ResourceNotFoundException.class,
                () -> contractService.getById(1L, "bob"),
                "Bob, Alice'in sözleşmesine erişememeli"
        );

        verify(contractRepository).findById(1L);
        verifyNoMoreInteractions(contractRepository);
    }

    @Test
    @DisplayName("IDOR — başka kullanıcının sözleşmesini güncelleme 404 döner")
    void update_otherUsersContract_throwsNotFound() {
        User alice = user("alice");
        Contract alicesContract = contract(2L, alice);

        when(contractRepository.findById(2L)).thenReturn(Optional.of(alicesContract));

        assertThrows(
                ResourceNotFoundException.class,
                () -> contractService.update(2L, null, "bob"),
                "Bob, Alice'in sözleşmesini güncelleyememeli"
        );
    }

    @Test
    @DisplayName("IDOR — başka kullanıcının sözleşmesini silme 404 döner")
    void delete_otherUsersContract_throwsNotFound() {
        User alice = user("alice");
        Contract alicesContract = contract(3L, alice);

        when(contractRepository.findById(3L)).thenReturn(Optional.of(alicesContract));

        assertThrows(
                ResourceNotFoundException.class,
                () -> contractService.delete(3L, "bob"),
                "Bob, Alice'in sözleşmesini silememeli"
        );
    }

    // ── Sahiplik doğrulama — kendi sözleşmesi erişilebilir ──────────────────

    @Test
    @DisplayName("Kullanıcı kendi sözleşmesine erişebilmeli")
    void getById_ownContract_doesNotThrow() {
        User alice = user("alice");
        Contract alicesContract = contract(4L, alice);

        when(contractRepository.findById(4L)).thenReturn(Optional.of(alicesContract));

        // ResourceNotFoundException fırlatılmamalı; diğer exception'lar burada önemli değil
        assertDoesNotThrow(() -> contractService.getById(4L, "alice"));
    }

    // ── Self-approval önleme ─────────────────────────────────────────────────

    @Test
    @DisplayName("Self-approval — kullanıcı kendi sözleşmesini onaylayamaz")
    void approve_ownContract_throwsUnauthorized() {
        User alice = user("alice");
        Contract alicesContract = contract(5L, alice);
        alicesContract.setStatus("PENDING");

        when(contractRepository.findById(5L)).thenReturn(Optional.of(alicesContract));

        assertThrows(
                UnauthorizedException.class,
                () -> contractService.approve(5L, "alice"),
                "Alice kendi sözleşmesini onaylayamamalı"
        );
    }

    @Test
    @DisplayName("Self-rejection — kullanıcı kendi sözleşmesini reddedemez")
    void reject_ownContract_throwsUnauthorized() {
        User alice = user("alice");
        Contract alicesContract = contract(6L, alice);
        alicesContract.setStatus("PENDING");

        when(contractRepository.findById(6L)).thenReturn(Optional.of(alicesContract));

        assertThrows(
                UnauthorizedException.class,
                () -> contractService.reject(6L, "alice"),
                "Alice kendi sözleşmesini reddedememeli"
        );
    }

    // ── Onay yetki regresyon ─────────────────────────────────────────────────

    @Test
    @DisplayName("Üçüncü taraf onay — karşı taraf TC Kimlik eşleşmeyince UnauthorizedException fırlatılır")
    void approve_thirdParty_not_counterparty_throws_unauthorized() {
        User alice = user("alice");
        Contract alicesContract = contract(10L, alice);
        alicesContract.setStatus("PENDING");
        alicesContract.setCounterpartyTcKimlik("11111111110"); // beklenen karşı taraf TC

        User charlie = user("charlie");
        charlie.setTcKimlik("99999999990"); // eşleşmiyor

        IdentityVerification charliVerification = new IdentityVerification();
        charliVerification.setStatus("VERIFIED");

        when(contractRepository.findById(10L)).thenReturn(Optional.of(alicesContract));
        when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(charlie));
        when(verificationRepository.findByUserId(charlie.getId())).thenReturn(Optional.of(charliVerification));

        assertThrows(
                UnauthorizedException.class,
                () -> contractService.approve(10L, "charlie"),
                "TC Kimlik uyuşmazsa approve UnauthorizedException fırlatmalı"
        );
    }

    @Test
    @DisplayName("İdempotency — zaten APPROVED sözleşmeyi tekrar onaylama BadRequestException fırlatır")
    void approve_already_approved_throws_bad_request() {
        User alice = user("alice");
        Contract alicesContract = contract(11L, alice);
        alicesContract.setStatus("APPROVED");

        when(contractRepository.findById(11L)).thenReturn(Optional.of(alicesContract));

        assertThrows(
                BadRequestException.class,
                () -> contractService.approve(11L, "bob"),
                "Zaten onaylanmış sözleşmeyi tekrar onaylamak BadRequestException fırlatmalı"
        );

        verifyNoInteractions(userRepository, verificationRepository);
    }

    @Test
    @DisplayName("TC Kimlik gate — tcKimlik null kullanıcı approve yapamazsa BadRequestException fırlatılır")
    void approve_approver_missing_tc_kimlik_throws_bad_request() {
        User alice = user("alice");
        Contract alicesContract = contract(12L, alice);
        alicesContract.setStatus("PENDING");
        alicesContract.setCounterpartyTcKimlik("11111111110");

        User bob = user("bob");
        bob.setTcKimlik(null); // kimlik doğrulaması yapılmamış

        when(contractRepository.findById(12L)).thenReturn(Optional.of(alicesContract));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));

        assertThrows(
                BadRequestException.class,
                () -> contractService.approve(12L, "bob"),
                "TC Kimlik olmayan kullanıcı approve yapamazsa BadRequestException fırlatmalı"
        );
    }

    // ── Yardımcı metodlar ────────────────────────────────────────────────────

    private User user(String username) {
        User u = new User();
        u.setId((long) username.hashCode());
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        return u;
    }

    private Contract contract(Long id, User owner) {
        Contract c = new Contract();
        c.setId(id);
        c.setTitle("Test Sözleşmesi");
        c.setStatus("DRAFT");
        c.setUser(owner);
        return c;
    }
}
