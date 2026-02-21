package com.earzuhal.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "identity_verifications")
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** TC kimlik numarasının maskelenmiş hali: "123*****01" */
    @Column(name = "tc_no_masked", length = 20)
    private String tcNoMasked;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** NFC | MRZ | MANUAL */
    @Column(name = "verification_method", length = 20)
    private String verificationMethod;

    /** VERIFIED | FAILED | PENDING */
    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @Column(name = "verified_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
