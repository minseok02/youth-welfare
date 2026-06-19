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
    @Column(columnDefinition = "TEXT")
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
    @Builder.Default
    private boolean searchYouthRelevant = true;

    private Long apiViewCount;
    @Builder.Default
    private Integer viewCount = 0;

    private LocalDateTime registeredAt;
    private LocalDateTime lastModifiedAt;

    public void updateStatus(ServiceStatus status) {
        this.status = status;
    }

    public void increaseViewCount() {
        if (this.viewCount == null) {
            this.viewCount = 1;
            return;
        }
        this.viewCount += 1;
    }

    /** 수집 배치에서 기존 레코드 필드를 최신 API 데이터로 덮어쓴다 */
    public void updateFromCollect(WelfareService source) {
        this.title = source.title;
        this.description = source.description;
        this.supportContent = source.supportContent;
        this.categoryMain = source.categoryMain;
        this.categorySub = source.categorySub;
        this.keyword = source.keyword;
        this.unifiedCategory = source.unifiedCategory;
        this.hostOrg = source.hostOrg;
        this.operatingOrg = source.operatingOrg;
        this.minAge = source.minAge;
        this.maxAge = source.maxAge;
        this.minIncome = source.minIncome;
        this.maxIncome = source.maxIncome;
        this.startDate = source.startDate;
        this.endDate = source.endDate;
        this.applyStartDate = source.applyStartDate;
        this.applyEndDate = source.applyEndDate;
        this.applyMethodName = source.applyMethodName;
        this.lifeStage = source.lifeStage;
        this.supportCycle = source.supportCycle;
        this.provisionType = source.provisionType;
        this.isOnlineApply = source.isOnlineApply;
        this.searchYouthRelevant = source.searchYouthRelevant;
        this.detailUrl = source.detailUrl;
        this.apiViewCount = source.apiViewCount;
        this.registeredAt = source.registeredAt;
        this.lastModifiedAt = source.lastModifiedAt;
    }

    public void updateSearchYouthRelevant(boolean searchYouthRelevant) {
        this.searchYouthRelevant = searchYouthRelevant;
    }

    public void applyAdminApplicationPeriod(LocalDate applyStartDate, LocalDate applyEndDate) {
        this.applyStartDate = applyStartDate;
        this.applyEndDate = applyEndDate;
    }

    public void applyAdminDetailUrl(String detailUrl) {
        this.detailUrl = detailUrl;
    }

    public void applyAdminEligibilityText(String eligibilityText) {
        this.description = eligibilityText;
    }

    public void applyDetailFallbacks(String supportContent,
                                     String applyMethodName,
                                     Integer minAge,
                                     Integer maxAge,
                                     LocalDate applyEndDate,
                                     Boolean isOnlineApply,
                                     String detailUrl) {
        Integer normalizedMinAge = normalizePositiveAge(minAge);
        Integer normalizedMaxAge = normalizePositiveAge(maxAge);
        if ((this.supportContent == null || this.supportContent.isBlank()) && supportContent != null && !supportContent.isBlank()) {
            this.supportContent = supportContent;
        }
        if ((this.applyMethodName == null || this.applyMethodName.isBlank()) && applyMethodName != null && !applyMethodName.isBlank()) {
            this.applyMethodName = applyMethodName;
        }
        repairInvalidAgeRange(normalizedMinAge, normalizedMaxAge);
        if (this.minAge == null && normalizedMinAge != null) {
            this.minAge = normalizedMinAge;
        }
        if (this.maxAge == null && normalizedMaxAge != null) {
            this.maxAge = normalizedMaxAge;
        }
        if (this.applyEndDate == null && applyEndDate != null) {
            this.applyEndDate = applyEndDate;
        }
        if (this.isOnlineApply == null && isOnlineApply != null) {
            this.isOnlineApply = isOnlineApply;
        }
        if ((this.detailUrl == null || this.detailUrl.isBlank()) && detailUrl != null && !detailUrl.isBlank()) {
            this.detailUrl = detailUrl;
        }
    }

    private void repairInvalidAgeRange(Integer fallbackMinAge, Integer fallbackMaxAge) {
        if (!hasInvalidAgeRange()) {
            return;
        }
        if (fallbackMinAge != null && fallbackMaxAge != null && isValidAgeRange(fallbackMinAge, fallbackMaxAge)) {
            this.minAge = fallbackMinAge;
            this.maxAge = fallbackMaxAge;
            return;
        }
        if (fallbackMinAge != null && isValidAgeRange(fallbackMinAge, this.maxAge)) {
            this.minAge = fallbackMinAge;
        }
        if (fallbackMaxAge != null && isValidAgeRange(this.minAge, fallbackMaxAge)) {
            this.maxAge = fallbackMaxAge;
        }
        if (hasInvalidAgeRange() && fallbackMinAge == null && fallbackMaxAge != null) {
            this.minAge = null;
            this.maxAge = fallbackMaxAge;
            return;
        }
        if (hasInvalidAgeRange() && fallbackMinAge != null && fallbackMaxAge == null) {
            this.minAge = fallbackMinAge;
            this.maxAge = null;
            return;
        }
        if (hasInvalidAgeRange() && fallbackMinAge == null && fallbackMaxAge == null) {
            this.minAge = null;
            this.maxAge = null;
        }
    }

    private boolean hasInvalidAgeRange() {
        return this.minAge != null && this.maxAge != null && this.minAge > this.maxAge;
    }

    private boolean isValidAgeRange(Integer minAge, Integer maxAge) {
        return minAge == null || maxAge == null || minAge <= maxAge;
    }

    private Integer normalizePositiveAge(Integer age) {
        return age != null && age > 0 ? age : null;
    }

    public enum SourceType {
        YOUTH, BOKJIRO_CENTRAL, BOKJIRO_LOCAL, GOV24
    }

    public enum ServiceStatus {
        ACTIVE, UPCOMING, CLOSED
    }
}
