package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyLookupService {

    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional(readOnly = true)
    public WelfareService getRequiredService(Long serviceId) {
        return welfareServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
    }
}
