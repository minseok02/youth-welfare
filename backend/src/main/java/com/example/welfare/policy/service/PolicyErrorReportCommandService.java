package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicyErrorReportCreateRequest;
import com.example.welfare.policy.dto.PolicyErrorReportResponse;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyErrorReportCommandService {

    private final PolicyLookupService policyLookupService;
    private final PolicyErrorReportRepository policyErrorReportRepository;

    @Transactional
    public PolicyErrorReportResponse submit(Long userId,
                                            String userKey,
                                            Long policyId,
                                            PolicyErrorReportCreateRequest request) {
        if (userId == null || userKey == null || userKey.isBlank() || request == null || request.reasonCode() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        WelfareService policy = policyLookupService.getRequiredService(policyId);
        String normalizedNote = normalizeNote(request.note());

        PolicyErrorReport report = policyErrorReportRepository.save(PolicyErrorReport.builder()
                .policy(policy)
                .userId(userId)
                .userKey(userKey.trim())
                .reasonCode(request.reasonCode())
                .note(normalizedNote)
                .status(PolicyErrorReport.Status.OPEN)
                .build());

        return PolicyErrorReportResponse.from(report);
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 1000) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }
}
