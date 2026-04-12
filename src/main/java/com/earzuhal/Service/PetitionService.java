package com.earzuhal.Service;

import com.earzuhal.Model.Petition;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.PetitionRepository;
import com.earzuhal.dto.petition.PetitionRequest;
import com.earzuhal.dto.petition.PetitionResponse;
import com.earzuhal.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PetitionService {

    private final PetitionRepository petitionRepository;
    private final UserService userService;

    public PetitionService(PetitionRepository petitionRepository, UserService userService) {
        this.petitionRepository = petitionRepository;
        this.userService = userService;
    }

    @Transactional
    public PetitionResponse create(PetitionRequest request, String username) {
        User user = userService.getUserByUsernameOrEmail(username);

        Petition petition = new Petition();
        petition.setKurum(request.getKurum());
        petition.setKurumAdresi(request.getKurumAdresi());
        petition.setYetkili(request.getYetkili());
        petition.setKonu(request.getKonu());
        petition.setGovde(request.getGovde());
        petition.setStatus("DRAFT");
        petition.setUser(user);
        petition.setCreatedAt(OffsetDateTime.now());
        petition.setUpdatedAt(OffsetDateTime.now());

        return toResponse(petitionRepository.save(petition));
    }

    public List<PetitionResponse> getAllByUser(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        return petitionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PetitionResponse getById(Long id, String username) {
        Petition petition = findByIdAndVerifyOwnership(id, username);
        return toResponse(petition);
    }

    /** PDF için tam entity döndürür (user lazy-loaded olduğu için) */
    public Petition getEntityById(Long id, String username) {
        return findByIdAndVerifyOwnership(id, username);
    }

    @Transactional
    public PetitionResponse update(Long id, PetitionRequest request, String username) {
        Petition petition = findByIdAndVerifyOwnership(id, username);

        if (request.getKurum() != null)       petition.setKurum(request.getKurum());
        if (request.getKurumAdresi() != null)  petition.setKurumAdresi(request.getKurumAdresi());
        if (request.getYetkili() != null)      petition.setYetkili(request.getYetkili());
        if (request.getKonu() != null)         petition.setKonu(request.getKonu());
        if (request.getGovde() != null)        petition.setGovde(request.getGovde());
        petition.setUpdatedAt(OffsetDateTime.now());

        return toResponse(petitionRepository.save(petition));
    }

    @Transactional
    public void delete(Long id, String username) {
        Petition petition = findByIdAndVerifyOwnership(id, username);
        petitionRepository.delete(petition);
    }

    @Transactional
    public PetitionResponse complete(Long id, String username) {
        Petition petition = findByIdAndVerifyOwnership(id, username);
        petition.setStatus("COMPLETED");
        petition.setUpdatedAt(OffsetDateTime.now());
        return toResponse(petitionRepository.save(petition));
    }

    private Petition findByIdAndVerifyOwnership(Long id, String username) {
        Petition petition = petitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dilekçe bulunamadı, id: " + id));
        User user = userService.getUserByUsernameOrEmail(username);
        if (!petition.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Dilekçe bulunamadı, id: " + id);
        }
        return petition;
    }

    private PetitionResponse toResponse(Petition p) {
        return PetitionResponse.builder()
                .id(p.getId())
                .kurum(p.getKurum())
                .kurumAdresi(p.getKurumAdresi())
                .yetkili(p.getYetkili())
                .konu(p.getKonu())
                .govde(p.getGovde())
                .status(p.getStatus())
                .userId(p.getUser().getId())
                .ownerUsername(p.getUser().getUsername())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
