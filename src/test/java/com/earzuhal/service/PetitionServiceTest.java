package com.earzuhal.service;

import com.earzuhal.Model.Petition;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.PetitionRepository;
import com.earzuhal.dto.petition.PetitionRequest;
import com.earzuhal.dto.petition.PetitionResponse;
import com.earzuhal.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PetitionService birim testleri.
 *
 * Kapsam: CRUD işlemleri, sahiplik doğrulama (IDOR önleme), durum geçişleri.
 */
@ExtendWith(MockitoExtension.class)
class PetitionServiceTest {

    @Mock PetitionRepository petitionRepository;
    @Mock UserService userService;

    private PetitionService petitionService;

    @BeforeEach
    void setUp() {
        petitionService = new PetitionService(petitionRepository, userService);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — geçerli istek DRAFT dilekçe oluşturur ve kaydeder")
    void create_validRequest_returnsDraftPetition() {
        User user = user("ali");
        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(user);
        when(petitionRepository.save(any(Petition.class))).thenAnswer(inv -> {
            Petition p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PetitionRequest req = request("T.C. Adalet Bakanlığı", "Maaş itirazı", "Maaşım eksik ödendi.");
        PetitionResponse resp = petitionService.create(req, "ali");

        assertEquals("DRAFT", resp.getStatus());
        assertEquals("T.C. Adalet Bakanlığı", resp.getKurum());
        assertEquals("Maaş itirazı", resp.getKonu());
        assertEquals("ali", resp.getOwnerUsername());

        ArgumentCaptor<Petition> captor = ArgumentCaptor.forClass(Petition.class);
        verify(petitionRepository).save(captor.capture());
        assertSame(user, captor.getValue().getUser());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById — kullanıcı kendi dilekçesini okuyabilir")
    void getById_ownPetition_returnsCorrectResponse() {
        User ali = user("ali");
        Petition p = petition(10L, ali);

        when(petitionRepository.findById(10L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(ali);

        PetitionResponse resp = petitionService.getById(10L, "ali");

        assertEquals(10L, resp.getId());
        assertEquals("Test Dilekçesi", resp.getKonu());
    }

    @Test
    @DisplayName("IDOR — başka kullanıcının dilekçesine getById isteği 404 döner (kaynak maskeleme)")
    void getById_otherUsersPetition_throwsNotFound() {
        User ali = user("ali");
        User veli = user("veli");
        Petition p = petition(11L, ali);

        when(petitionRepository.findById(11L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("veli")).thenReturn(veli);

        assertThrows(
                ResourceNotFoundException.class,
                () -> petitionService.getById(11L, "veli"),
                "Veli, Ali'nin dilekçesine erişememeli"
        );
    }

    @Test
    @DisplayName("getById — var olmayan id için ResourceNotFoundException fırlatılır")
    void getById_nonExistentId_throwsNotFound() {
        when(petitionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> petitionService.getById(999L, "ali"));
    }

    // ── getEntityById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEntityById — PDF için ham entity döner, sahiplik kontrolü yapılır")
    void getEntityById_ownPetition_returnsEntity() {
        User ali = user("ali");
        Petition p = petition(20L, ali);

        when(petitionRepository.findById(20L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(ali);

        Petition result = petitionService.getEntityById(20L, "ali");

        assertSame(p, result);
    }

    @Test
    @DisplayName("IDOR — getEntityById başka kullanıcı için 404 fırlatır")
    void getEntityById_otherUser_throwsNotFound() {
        User ali = user("ali");
        User veli = user("veli");
        Petition p = petition(21L, ali);

        when(petitionRepository.findById(21L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("veli")).thenReturn(veli);

        assertThrows(ResourceNotFoundException.class,
                () -> petitionService.getEntityById(21L, "veli"));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update — null olmayan alanları seçici olarak günceller")
    void update_partialFields_onlyUpdatesNonNullFields() {
        User ali = user("ali");
        Petition p = petition(30L, ali);
        String originalGovde = p.getGovde();

        when(petitionRepository.findById(30L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(ali);
        when(petitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PetitionRequest req = new PetitionRequest();
        req.setKonu("Güncellenmiş Konu");
        // govde null → değişmemeli

        PetitionResponse resp = petitionService.update(30L, req, "ali");

        assertEquals("Güncellenmiş Konu", resp.getKonu());
        assertEquals(originalGovde, resp.getGovde(), "govde null'dan güncellenmemeli");
    }

    @Test
    @DisplayName("IDOR — başka kullanıcının dilekçesini güncelleme 404 döner")
    void update_otherUsersPetition_throwsNotFound() {
        User ali = user("ali");
        User veli = user("veli");
        Petition p = petition(31L, ali);

        when(petitionRepository.findById(31L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("veli")).thenReturn(veli);

        assertThrows(ResourceNotFoundException.class,
                () -> petitionService.update(31L, new PetitionRequest(), "veli"));

        verify(petitionRepository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — kullanıcı kendi dilekçesini silebilir")
    void delete_ownPetition_callsRepositoryDelete() {
        User ali = user("ali");
        Petition p = petition(40L, ali);

        when(petitionRepository.findById(40L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(ali);

        assertDoesNotThrow(() -> petitionService.delete(40L, "ali"));
        verify(petitionRepository).delete(p);
    }

    @Test
    @DisplayName("IDOR — başka kullanıcının dilekçesini silme 404 döner, delete çağrılmaz")
    void delete_otherUsersPetition_throwsNotFound_noDeletion() {
        User ali = user("ali");
        User veli = user("veli");
        Petition p = petition(41L, ali);

        when(petitionRepository.findById(41L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("veli")).thenReturn(veli);

        assertThrows(ResourceNotFoundException.class,
                () -> petitionService.delete(41L, "veli"));

        verify(petitionRepository, never()).delete(any(Petition.class));
    }

    // ── complete ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("complete — DRAFT → COMPLETED durum geçişi")
    void complete_draftPetition_setsStatusCompleted() {
        User ali = user("ali");
        Petition p = petition(50L, ali);
        assertEquals("DRAFT", p.getStatus());

        when(petitionRepository.findById(50L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(ali);
        when(petitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PetitionResponse resp = petitionService.complete(50L, "ali");

        assertEquals("COMPLETED", resp.getStatus());

        ArgumentCaptor<Petition> captor = ArgumentCaptor.forClass(Petition.class);
        verify(petitionRepository).save(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("IDOR — başka kullanıcının dilekçesini tamamlama 404 döner")
    void complete_otherUsersPetition_throwsNotFound() {
        User ali = user("ali");
        User veli = user("veli");
        Petition p = petition(51L, ali);

        when(petitionRepository.findById(51L)).thenReturn(Optional.of(p));
        when(userService.getUserByUsernameOrEmail("veli")).thenReturn(veli);

        assertThrows(ResourceNotFoundException.class,
                () -> petitionService.complete(51L, "veli"));

        verify(petitionRepository, never()).save(any());
    }

    // ── getAllByUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllByUser — yalnızca o kullanıcının dilekçeleri döner")
    void getAllByUser_returnsOnlyUsersPetitions() {
        User ali = user("ali");
        Petition p1 = petition(60L, ali);
        Petition p2 = petition(61L, ali);

        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(ali);
        when(petitionRepository.findByUserIdOrderByCreatedAtDesc(ali.getId()))
                .thenReturn(List.of(p1, p2));

        List<PetitionResponse> result = petitionService.getAllByUser("ali");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "ali".equals(r.getOwnerUsername())));
    }

    @Test
    @DisplayName("getAllByUser — dilekçesi olmayan kullanıcı için boş liste döner")
    void getAllByUser_noPetitions_returnsEmptyList() {
        User ali = user("ali");

        when(userService.getUserByUsernameOrEmail("ali")).thenReturn(ali);
        when(petitionRepository.findByUserIdOrderByCreatedAtDesc(ali.getId()))
                .thenReturn(List.of());

        List<PetitionResponse> result = petitionService.getAllByUser("ali");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Yardımcı metodlar ─────────────────────────────────────────────────────

    private User user(String username) {
        User u = new User();
        u.setId((long) Math.abs(username.hashCode()));
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        return u;
    }

    private Petition petition(Long id, User owner) {
        Petition p = new Petition();
        p.setId(id);
        p.setKurum("T.C. Çalışma Bakanlığı");
        p.setKonu("Test Dilekçesi");
        p.setGovde("Bu bir test dilekçesidir.");
        p.setStatus("DRAFT");
        p.setUser(owner);
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedAt(OffsetDateTime.now());
        return p;
    }

    private PetitionRequest request(String kurum, String konu, String govde) {
        PetitionRequest req = new PetitionRequest();
        req.setKurum(kurum);
        req.setKonu(konu);
        req.setGovde(govde);
        return req;
    }
}
