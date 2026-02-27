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
@Table(name = "disclaimer_acceptances")
public class DisclaimerAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Yasal uyarı metninin versiyonu (metin değiştiğinde eski kabuller geçersiz sayılır) */
    @Column(nullable = false, length = 10)
    private String version;

    @Column(name = "accepted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime acceptedAt = OffsetDateTime.now();

    /** Kabul yapılan platform: WEB veya MOBILE */
    @Column(length = 20)
    private String platform;
}
