package com.earzuhal.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesRequest {
    private Boolean email;
    private Boolean sms;
    private Boolean push;
    private Boolean contractUpdates;
    private Boolean approvalRequests;
    private Boolean marketing;
}
