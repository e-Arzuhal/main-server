package com.earzuhal.Service;

import com.earzuhal.Model.DisclaimerAcceptance;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.DisclaimerRepository;
import com.earzuhal.dto.disclaimer.DisclaimerStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class DisclaimerService {

    @Value("${disclaimer.version}")
    private String currentVersion;

    private final DisclaimerRepository disclaimerRepository;
    private final UserService userService;

    public DisclaimerService(DisclaimerRepository disclaimerRepository, UserService userService) {
        this.disclaimerRepository = disclaimerRepository;
        this.userService = userService;
    }

    public boolean hasAccepted(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        return disclaimerRepository.existsByUserIdAndVersion(user.getId(), currentVersion);
    }

    @Transactional
    public DisclaimerStatusResponse accept(String username, String platform) {
        User user = userService.getUserByUsernameOrEmail(username);
        if (!disclaimerRepository.existsByUserIdAndVersion(user.getId(), currentVersion)) {
            DisclaimerAcceptance acceptance = new DisclaimerAcceptance();
            acceptance.setUser(user);
            acceptance.setVersion(currentVersion);
            acceptance.setAcceptedAt(OffsetDateTime.now());
            acceptance.setPlatform(platform != null ? platform.toUpperCase() : "UNKNOWN");
            disclaimerRepository.save(acceptance);
        }
        return DisclaimerStatusResponse.builder()
                .accepted(true)
                .version(currentVersion)
                .acceptedAt(OffsetDateTime.now())
                .build();
    }

    public DisclaimerStatusResponse getStatus(String username) {
        User user = userService.getUserByUsernameOrEmail(username);
        Optional<DisclaimerAcceptance> acceptance =
                disclaimerRepository.findFirstByUserIdAndVersionOrderByAcceptedAtDesc(user.getId(), currentVersion);
        return acceptance
                .map(a -> DisclaimerStatusResponse.builder()
                        .accepted(true)
                        .version(a.getVersion())
                        .acceptedAt(a.getAcceptedAt())
                        .build())
                .orElseGet(() -> DisclaimerStatusResponse.builder()
                        .accepted(false)
                        .version(currentVersion)
                        .acceptedAt(null)
                        .build());
    }
}
