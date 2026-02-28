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
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 50)
    private String type;

    @Column(length = 20)
    private String status = "DRAFT";

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 100)
    private String amount;

    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;

    @Column(name = "counterparty_role", length = 50)
    private String counterpartyRole;

    /**
     * Madde açıklamaları — JSON string (List&lt;ClauseExplanationItem&gt;).
     * Sözleşme analiz bağlamıyla oluşturulduğunda doldurulur; null olabilir.
     */
    @Column(name = "clause_explanations", columnDefinition = "TEXT")
    private String clauseExplanations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
