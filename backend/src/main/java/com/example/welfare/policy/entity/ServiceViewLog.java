package com.example.welfare.policy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_view_logs", indexes = {
        @Index(name = "idx_svl_service_viewed", columnList = "service_id, viewed_at"),
        @Index(name = "idx_svl_user_key_service_viewed", columnList = "user_key, service_id, viewed_at"),
        @Index(name = "idx_svl_fp_service_viewed", columnList = "client_fingerprint, service_id, viewed_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ServiceViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Column(name = "user_key", length = 32)
    private String userKey;

    @Column(name = "client_fingerprint", nullable = false, length = 64)
    private String clientFingerprint;

    @Column(name = "viewed_at", nullable = false)
    @Builder.Default
    private LocalDateTime viewedAt = LocalDateTime.now();
}
