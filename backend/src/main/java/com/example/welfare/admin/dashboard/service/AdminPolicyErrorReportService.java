package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPolicyErrorReportService {

    private final PolicyErrorReportRepository policyErrorReportRepository;

    @Transactional(readOnly = true)
    public AdminPolicyErrorReportResponse getRecentReports(Integer requestedLimit) {
        int limit = requestedLimit == null ? 10 : Math.max(1, Math.min(requestedLimit, 20));
        long openCount = policyErrorReportRepository.countByStatus(PolicyErrorReport.Status.OPEN);
        List<AdminPolicyErrorReportResponse.Item> items = policyErrorReportRepository
                .findByStatusOrderByCreatedAtDesc(PolicyErrorReport.Status.OPEN, PageRequest.of(0, limit))
                .stream()
                .map(report -> new AdminPolicyErrorReportResponse.Item(
                        report.getId(),
                        report.getPolicy().getId(),
                        report.getPolicy().getTitle(),
                        report.getPolicy().getSourceType().name(),
                        report.getPolicy().getSourceId(),
                        report.getReasonCode().name(),
                        report.getReasonCode().getLabel(),
                        report.getNote(),
                        report.getUserKey(),
                        report.getCreatedAt()
                ))
                .toList();
        return new AdminPolicyErrorReportResponse(openCount, items);
    }
}
