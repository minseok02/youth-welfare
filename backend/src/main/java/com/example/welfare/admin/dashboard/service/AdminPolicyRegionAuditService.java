package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionAuditResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionResponse;
import com.example.welfare.global.service.AppSchedulerGate;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPolicyRegionAuditService {

    private static final String SYSTEM_USER_KEY = "system-region-audit";
    private static final int DEFAULT_SCAN_LIMIT = 50_000;
    private static final int MAX_SCAN_LIMIT = 50_000;
    private static final int MAX_REPORTS_PER_RUN = 50;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final WelfareServiceRepository welfareServiceRepository;
    private final PolicyErrorReportRepository policyErrorReportRepository;
    private final AppSchedulerGate appSchedulerGate;

    @Scheduled(cron = "0 20 5 ? * MON", zone = "Asia/Seoul")
    public void runScheduledRegionAudit() {
        if (!appSchedulerGate.shouldRun("AdminPolicyRegionAuditService.runScheduledRegionAudit")) {
            return;
        }
        try {
            AdminPolicyRegionAuditResponse response = runRegionAudit(DEFAULT_SCAN_LIMIT);
            log.info("[PolicyRegionAudit] scheduled completed scanned={} candidates={} created={} skippedExisting={}",
                    response.scannedCount(),
                    response.candidateCount(),
                    response.createdReportCount(),
                    response.skippedExistingReportCount());
        } catch (Exception e) {
            log.warn("[PolicyRegionAudit] scheduled failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    @Transactional
    public AdminPolicyRegionAuditResponse runRegionAudit(Integer requestedLimit) {
        int scanLimit = requestedLimit == null ? DEFAULT_SCAN_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_SCAN_LIMIT));
        List<PolicyRegionAuditRow> rows = jdbcTemplate.query("""
                SELECT ws.id,
                       ws.title,
                       ws.source_type,
                       ws.source_id,
                       ws.host_org,
                       ws.operating_org,
                       ws.description,
                       ws.support_content,
                       ws.apply_method_name,
                       COUNT(sr.id) AS region_count
                  FROM welfare_services ws
                  LEFT JOIN service_regions sr ON sr.service_id = ws.id
                 WHERE ws.status IN ('ACTIVE', 'UPCOMING')
                   AND ws.source_type = 'GOV24'
                   AND NOT EXISTS (
                         SELECT 1
                           FROM policy_region_corrections prc
                          WHERE prc.service_id = ws.id
                            AND prc.active = TRUE
                   )
                 GROUP BY ws.id, ws.title, ws.source_type, ws.source_id, ws.host_org, ws.operating_org,
                          ws.description, ws.support_content, ws.apply_method_name
                 ORDER BY ws.updated_at DESC NULLS LAST, ws.id DESC
                 LIMIT :limit
                """, new MapSqlParameterSource("limit", scanLimit), (rs, rowNum) -> new PolicyRegionAuditRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("host_org"),
                rs.getString("operating_org"),
                rs.getString("description"),
                rs.getString("support_content"),
                rs.getString("apply_method_name"),
                rs.getInt("region_count")
        ));

        List<AdminPolicyRegionAuditResponse.Item> candidates = new ArrayList<>();
        int created = 0;
        int skippedExisting = 0;
        for (PolicyRegionAuditRow row : rows) {
            RegionAuditCandidate candidate = inspect(row);
            if (candidate == null) {
                continue;
            }
            candidates.add(candidate.toResponse());
            boolean exists = policyErrorReportRepository.existsOpenSystemRegionAuditReport(
                    row.policyId(),
                    PolicyErrorReport.ReasonCode.REGION_MISMATCH,
                    PolicyErrorReport.Status.OPEN,
                    SYSTEM_USER_KEY
            );
            if (exists) {
                skippedExisting++;
                continue;
            }
            if (created >= MAX_REPORTS_PER_RUN) {
                continue;
            }
            WelfareService policy = welfareServiceRepository.getReferenceById(row.policyId());
            policyErrorReportRepository.save(PolicyErrorReport.builder()
                    .policy(policy)
                    .userKey(SYSTEM_USER_KEY)
                    .reasonCode(PolicyErrorReport.ReasonCode.REGION_MISMATCH)
                    .note(candidate.note())
                    .status(PolicyErrorReport.Status.OPEN)
                    .build());
            created++;
        }

        return new AdminPolicyRegionAuditResponse(
                rows.size(),
                candidates.size(),
                created,
                skippedExisting,
                candidates.stream().limit(50).toList()
        );
    }

    private RegionAuditCandidate inspect(PolicyRegionAuditRow row) {
        List<RegionCodeUtil.RegionName> suggestedRegions = RegionCodeUtil.inferRegionNamesFromText(
                row.title(),
                row.hostOrg(),
                row.operatingOrg(),
                row.description(),
                row.supportContent(),
                row.applyMethodName()
        );
        if (suggestedRegions.isEmpty()) {
            return null;
        }
        if (row.regionCount() == 0) {
            return new RegionAuditCandidate(
                    row,
                    "지역 row 없음 + 정책 필드 지역 신호",
                    suggestedRegions
            );
        }
        if (row.regionCount() >= 20 && suggestedRegions.size() <= 10) {
            return new RegionAuditCandidate(
                    row,
                    "광범위 지역 row + 정책 필드 특정 지역 신호",
                    suggestedRegions
            );
        }
        return null;
    }

    private record PolicyRegionAuditRow(
            Long policyId,
            String title,
            String sourceType,
            String sourceId,
            String hostOrg,
            String operatingOrg,
            String description,
            String supportContent,
            String applyMethodName,
            int regionCount
    ) {
    }

    private record RegionAuditCandidate(
            PolicyRegionAuditRow row,
            String reason,
            List<RegionCodeUtil.RegionName> suggestedRegions
    ) {
        private AdminPolicyRegionAuditResponse.Item toResponse() {
            return new AdminPolicyRegionAuditResponse.Item(
                    row.policyId(),
                    row.title(),
                    row.sourceType(),
                    row.sourceId(),
                    reason,
                    suggestedRegions.stream()
                            .map(region -> new AdminPolicyRegionCorrectionResponse.RegionItem(
                                    region.regionCode(),
                                    region.sidoName(),
                                    region.sggName()
                            ))
                            .toList()
            );
        }

        private String note() {
            String regionLabel = suggestedRegions.stream()
                    .limit(10)
                    .map(region -> region.sidoName() + " " + region.sggName() + "(" + region.regionCode() + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("후보 없음");
            return "[자동 지역감사] " + reason + " / 후보: " + regionLabel;
        }
    }
}
