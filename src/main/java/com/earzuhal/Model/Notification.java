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
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id,is_read")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
