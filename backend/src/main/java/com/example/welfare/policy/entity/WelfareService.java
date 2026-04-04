package com.example.welfare.policy.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "welfare_services")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class WelfareService extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    @Column(nullable = false)
    private String sourceId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String unifiedCategory; // 통합 카테고리

    private String categoryMain;
    private String categorySub;
    private String keyword;

    private String hostOrg;
    private String operatingOrg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ServiceStatus status = ServiceStatus.ACTIVE;

    private Integer minAge;
    private Integer maxAge;
    private Integer minIncome; // 소득분위 하한
    private Integer maxIncome; // 소득분위 상한

    private String supportContent;  // 지원 내용 요약
    private String applyMethodName; // 신청 방법

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate applyStartDate;
    private LocalDate applyEndDate;

    private String lifeStage;        // 생애주기 태그 (콤마 구분)
    private String detailUrl;
    private String supportCycle;
    private String provisionType;
    private Boolean isOnlineApply;

    private Long apiViewCount;

    private LocalDateTime registeredAt;
    private LocalDateTime lastModifiedAt;

    public void updateStatus(ServiceStatus status) {
        this.status = status;
    }

    public enum SourceType {
        YOUTH, BOKJIRO_CENTRAL, BOKJIRO_LOCAL
    }

    public enum ServiceStatus {
        ACTIVE, UPCOMING, CLOSED
    }
}
