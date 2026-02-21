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
@Table(name = "petitions")
public class Petition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Dilekçenin gönderileceği kurum/makam (örn: "T.C. Adalet Bakanlığı") */
    @Column(nullable = false, length = 200)
    private String kurum;

    /** Kurumun adresi */
    @Column(length = 300)
    private String kurumAdresi;

    /** Hitap edilecek kişi/makam (örn: "Sayın Müdür", "Sayın Yetkili") */
    @Column(length = 150)
    private String yetkili;

    /** Dilekçe konusu */
    @Column(nullable = false, length = 300)
    private String konu;

    /** Dilekçe gövde metni */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String govde;

    /** DRAFT, COMPLETED */
    @Column(length = 20)
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
