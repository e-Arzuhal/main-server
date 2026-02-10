package com.earzuhal.dto.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractStatsResponse {

    private long totalCount;
    private long draftCount;
    private long pendingCount;
    private long approvedCount;
    private long completedCount;
    private long rejectedCount;
}
