package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyLookupReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyLookupService {

    private final PolicyLookupReadRepository policyLookupReadRepository;

    @Transactional(readOnly = true)
    public WelfareService getRequiredService(Long serviceId) {
        return policyLookupReadRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
    }
}
