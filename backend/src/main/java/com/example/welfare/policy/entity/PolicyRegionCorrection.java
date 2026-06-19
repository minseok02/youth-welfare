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
        name = "policy_region_corrections",
        indexes = {
                @Index(name = "idx_prc_active_service", columnList = "active, service_id"),
                @Index(name = "idx_prc_reason_report", columnList = "reason_report_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PolicyRegionCorrection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_scope", nullable = false, length = 20)
    private Scope correctionScope;

    @Column(name = "regions_json", nullable = false, columnDefinition = "TEXT")
    private String regionsJson;

    @Column(name = "original_regions_json", nullable = false, columnDefinition = "TEXT")
    private String originalRegionsJson;

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

    public void replace(Scope correctionScope,
                        String regionsJson,
                        String originalRegionsJson,
                        PolicyErrorReport reasonReport,
                        String correctionNote,
                        String updatedByUserKey) {
        this.correctionScope = correctionScope;
        this.regionsJson = regionsJson;
        if (this.originalRegionsJson == null || this.originalRegionsJson.isBlank()) {
            this.originalRegionsJson = originalRegionsJson;
        }
        this.reasonReport = reasonReport;
        this.correctionNote = correctionNote;
        this.updatedByUserKey = updatedByUserKey;
        this.active = true;
    }

    public void deactivate(String updatedByUserKey, String correctionNote) {
        this.active = false;
        this.updatedByUserKey = updatedByUserKey;
        if (correctionNote != null && !correctionNote.isBlank()) {
            this.correctionNote = correctionNote;
        }
    }

    public enum Scope {
        REGIONS,
        NATIONWIDE
    }
}
