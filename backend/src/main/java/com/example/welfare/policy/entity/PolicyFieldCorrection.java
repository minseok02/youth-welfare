package com.example.welfare.policy.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "policy_field_corrections",
        indexes = {
                @Index(name = "idx_pfc_service_active", columnList = "service_id, active"),
                @Index(name = "idx_pfc_reason_report", columnList = "reason_report_id"),
                @Index(name = "idx_pfc_updated_at", columnList = "updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PolicyFieldCorrection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_type", nullable = false, length = 40)
    private Type correctionType;

    @Column(name = "original_json", nullable = false, columnDefinition = "TEXT")
    private String originalJson;

    @Column(name = "correction_json", nullable = false, columnDefinition = "TEXT")
    private String correctionJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reason_report_id")
    private PolicyErrorReport reasonReport;

    @Column(name = "correction_note", length = 1000)
    private String correctionNote;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by_user_key", nullable = false, length = 100)
    private String createdByUserKey;

    @Column(name = "updated_by_user_key", nullable = false, length = 100)
    private String updatedByUserKey;

    public enum Type {
        APPLICATION_PERIOD,
        DETAIL_URL,
        ELIGIBILITY,
        DUPLICATE_POLICY
    }
}
