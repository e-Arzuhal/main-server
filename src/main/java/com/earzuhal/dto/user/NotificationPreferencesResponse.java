package com.earzuhal.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferencesResponse {
    private Boolean email;
    private Boolean sms;
    private Boolean push;
    private Boolean contractUpdates;
    private Boolean approvalRequests;
    private Boolean marketing;
}
