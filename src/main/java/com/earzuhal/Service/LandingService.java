package com.earzuhal.Service;

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

    public LandingService(UserRepository userRepository,
                          ContractRepository contractRepository,
                          DemoRequestRepository demoRequestRepository) {
        this.userRepository = userRepository;
        this.contractRepository = contractRepository;
        this.demoRequestRepository = demoRequestRepository;
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
    }

    public void subscribeNewsletter(NewsletterRequest request) {
        log.info("Bülten aboneliği: {}", request.getEmail());
    }
}
