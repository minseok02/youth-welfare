package com.example.welfare.policy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_logs", indexes = {
        @Index(name = "idx_sl_searched", columnList = "searched_at"),
        @Index(name = "idx_sl_user_key_searched", columnList = "user_key, searched_at"),
        @Index(name = "idx_sl_keyword_searched", columnList = "keyword, searched_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", length = 32)
    private String userKey;

    @Column(name = "client_fingerprint", nullable = false, length = 64)
    private String clientFingerprint;

    @Column(nullable = false, length = 255)
    private String keyword;

    @Column(name = "result_count", nullable = false)
    private Long resultCount;

    @Column(name = "status_filter", length = 16)
    private String statusFilter;

    @Column(name = "include_closed", nullable = false)
    private boolean includeClosed;

    @Column(length = 64)
    private String category;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "online_apply")
    private Boolean onlineApply;

    @Column(length = 64)
    private String sido;

    @Column(length = 64)
    private String sgg;

    @Column(name = "sort_key", length = 16)
    private String sortKey;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "page_size", nullable = false)
    private int pageSize;

    @Column(name = "searched_at", nullable = false)
    @Builder.Default
    private LocalDateTime searchedAt = LocalDateTime.now();
}
