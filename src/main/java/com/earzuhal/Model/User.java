package com.earzuhal.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "tc_kimlik")
        })
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(length = 20)
    private String role = "USER";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "tc_kimlik", length = 11, unique = true)
    private String tcKimlik;

        @Column(name = "notif_email")
        private Boolean notifEmail = true;

        @Column(name = "notif_sms")
        private Boolean notifSms = false;

        @Column(name = "notif_push")
        private Boolean notifPush = true;

        @Column(name = "notif_contract_updates")
        private Boolean notifContractUpdates = true;

        @Column(name = "notif_approval_requests")
        private Boolean notifApprovalRequests = true;

        @Column(name = "notif_marketing")
        private Boolean notifMarketing = false;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

}
