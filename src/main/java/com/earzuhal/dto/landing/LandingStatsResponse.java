package com.earzuhal.dto.landing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandingStatsResponse {
    private long totalUsers;
    private long totalContracts;
    private double uptimePercent;
}
