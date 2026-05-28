package com.example.welfare.policy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "recent_policy_views",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_recent_policy_views_user_service", columnNames = {"user_key", "service_id"})
        },
        indexes = {
                @Index(name = "idx_rpv_user_key_last_viewed", columnList = "user_key, last_viewed_at"),
                @Index(name = "idx_rpv_service", columnList = "service_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecentPolicyView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 32)
    private String userKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Column(name = "last_viewed_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastViewedAt = LocalDateTime.now();
}
