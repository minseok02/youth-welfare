package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * 추천 후보 추출 — SQL 필터 (pass/fail)
 * K=50건 선별 + 신규 정책 M=5건 강제 포함
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final int K = 50;
    private static final int FETCH_SIZE = 150; // 후처리 필터 감안해 넉넉히 조회

    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceTagRepository serviceTagRepository;

    @Transactional(readOnly = true)
    public List<WelfareService> retrieve(String clusterId, User user) {
        int age = calculateAge(user);
        int incomeLevel = user.getIncomeLevel() != null ? user.getIncomeLevel() : 5;

        List<WelfareService> rawCandidates;
        // sido만 있어도 지역 쿼리 사용 (regionCode는 nullable — LEFT JOIN 쿼리가 NULL 안전 처리)
        if (user.getSido() != null) {
            String regionCode = user.getRegionCode() != null ? user.getRegionCode() : "";
            rawCandidates = welfareServiceRepository.findCandidatesWithRegion(
                    age, incomeLevel,
                    regionCode, user.getSido(),
                    PageRequest.of(0, FETCH_SIZE));
        } else {
            rawCandidates = welfareServiceRepository.findCandidates(age, incomeLevel, PageRequest.of(0, FETCH_SIZE));
        }

        return applyExtractedAgeFilter(rawCandidates, age).stream()
                .limit(K)
                .collect(Collectors.toList());
    }

    private int calculateAge(User user) {
        if (user.getBirthDate() == null) return 25; // 기본값
        return LocalDate.now().getYear() - user.getBirthDate().getYear();
    }

    /**
     * 구조화된 나이 필드(min_age/max_age)가 비어있는 정책에 한해
     * KEYWORD의 COND_AGE_MIN_*, COND_AGE_MAX_* 토큰으로 보조 필터를 적용한다.
     */
    private List<WelfareService> applyExtractedAgeFilter(List<WelfareService> candidates, int userAge) {
        if (candidates.isEmpty()) return candidates;

        List<Long> ids = candidates.stream().map(WelfareService::getId).toList();
        Map<Long, List<String>> keywordMap = serviceTagRepository
                .findByServiceIdInAndTagType(ids, ServiceTag.TagType.KEYWORD)
                .stream()
                .collect(Collectors.groupingBy(
                        tag -> tag.getService().getId(),
                        Collectors.mapping(ServiceTag::getTagValue, Collectors.toList())
                ));

        return candidates.stream()
                .filter(service -> matchAgeConstraint(service, userAge, keywordMap.get(service.getId())))
                .collect(Collectors.toList());
    }

    private boolean matchAgeConstraint(WelfareService service, int userAge, List<String> keywords) {
        // 구조화 필드가 있으면 DB 단계에서 이미 필터링됨
        if (service.getMinAge() != null || service.getMaxAge() != null) return true;
        if (keywords == null || keywords.isEmpty()) return true;

        OptionalInt min = keywords.stream()
                .filter(v -> v.startsWith("COND_AGE_MIN_"))
                .map(v -> v.substring("COND_AGE_MIN_".length()))
                .mapToInt(this::safeInt)
                .filter(v -> v > 0)
                .max();

        OptionalInt max = keywords.stream()
                .filter(v -> v.startsWith("COND_AGE_MAX_"))
                .map(v -> v.substring("COND_AGE_MAX_".length()))
                .mapToInt(this::safeInt)
                .filter(v -> v > 0)
                .min();

        if (min.isPresent() && userAge < min.getAsInt()) return false;
        if (max.isPresent() && userAge > max.getAsInt()) return false;
        return true;
    }

    private int safeInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return -1;
        }
    }
}
