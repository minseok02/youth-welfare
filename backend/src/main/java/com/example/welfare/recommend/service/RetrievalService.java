package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 추천 후보 추출 — SQL 필터 (pass/fail)
 * K=50건 선별 + 신규 정책 M=5건 강제 포함
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final int K = 50;

    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional(readOnly = true)
    public List<WelfareService> retrieve(String clusterId, User user) {
        int age = calculateAge(user);
        int incomeLevel = user.getIncomeLevel() != null ? user.getIncomeLevel() : 5;

        if (user.getSido() != null && user.getRegionCode() != null) {
            return welfareServiceRepository.findCandidatesWithRegion(
                    age, incomeLevel,
                    user.getRegionCode(), user.getSido(),
                    PageRequest.of(0, K));
        } else {
            return welfareServiceRepository.findCandidates(age, incomeLevel, PageRequest.of(0, K));
        }
    }

    private int calculateAge(User user) {
        if (user.getBirthDate() == null) return 25; // 기본값
        return LocalDate.now().getYear() - user.getBirthDate().getYear();
    }
}
