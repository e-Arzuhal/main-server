package com.earzuhal.Service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.ContractRepository;
import com.earzuhal.Repository.IdentityVerificationRepository;
import com.earzuhal.Repository.UserRepository;
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

    private ContractService contractService;

    @BeforeEach
    void setUp() {
        contractService = new ContractService(
                contractRepository, userRepository, verificationRepository,
                userService, disclaimerService, explanationService,
                notificationService, new ObjectMapper()
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
