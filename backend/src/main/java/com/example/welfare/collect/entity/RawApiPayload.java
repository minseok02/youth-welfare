package com.example.welfare.collect.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import com.example.welfare.policy.entity.WelfareService;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "raw_api_payloads", uniqueConstraints = {
        @UniqueConstraint(name = "uq_raw_source", columnNames = {"source_type", "source_id", "api_category"})
}, indexes = {
        @Index(name = "idx_raw_fetched", columnList = "fetched_at"),
        @Index(name = "idx_raw_hash", columnList = "payload_hash")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RawApiPayload extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WelfareService.SourceType sourceType;

    @Column(nullable = false, length = 50)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiCategory apiCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, length = 64, columnDefinition = "VARCHAR(64)")
    private String payloadHash;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    public void updatePayload(String payloadJson, String payloadHash, LocalDateTime fetchedAt) {
        this.payloadJson = payloadJson;
        this.payloadHash = payloadHash;
        this.fetchedAt = fetchedAt;
    }

    public enum ApiCategory {
        LIST,
        DETAIL,
        SUPPORT
    }
}
