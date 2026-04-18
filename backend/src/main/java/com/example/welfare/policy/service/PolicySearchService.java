package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.service.YouthPolicyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicySearchService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 100;

    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceTagRepository serviceTagRepository;
    private final YouthPolicyFilter youthPolicyFilter;

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> search(String keyword, int page) {
        return search(keyword, null, null, null, null, null, null, null, page, DEFAULT_SEARCH_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> search(String keyword,
                                              String status,
                                              String category,
                                              String sourceType,
                                              Boolean onlineApply,
                                              String sido,
                                              String sgg,
                                              String sort,
                                              int page,
                                              int size) {
        // MySQL FULLTEXT 검색 (ngram 파서)
        // keyword는 Controller에서 trim 처리 후 전달됨
        String ftKeyword = buildFulltextKeyword(keyword);
        int limit = normalizeSize(size);
        int offset = Math.max(0, page) * limit;
        String normalizedStatus = normalizeStatus(status);
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalizedCategory = normalizeNullable(category);
        String normalizedSido = normalizeNullable(sido);
        String normalizedSgg = normalizeNullable(sgg);
        Integer onlineApplyFlag = onlineApply == null ? null : (onlineApply ? 1 : 0);
        String normalizedSort = normalizeSort(sort);

        List<WelfareService> results = welfareServiceRepository.searchByKeywordWithFilters(
                ftKeyword,
                normalizedStatus,
                normalizedCategory,
                normalizedSourceType,
                onlineApplyFlag,
                normalizedSido,
                normalizedSgg,
                normalizedSort,
                limit,
                offset);

        return filterYouthRelevant(results).stream()
                .map(PolicySummaryResponse::from)
                .collect(Collectors.toList());
    }

    private List<WelfareService> filterYouthRelevant(List<WelfareService> results) {
        if (results.isEmpty()) {
            return results;
        }

        List<Long> serviceIds = results.stream()
                .map(WelfareService::getId)
                .toList();

        Map<Long, List<ServiceTag>> tagsByServiceId = serviceTagRepository.findByServiceIdIn(serviceIds).stream()
                .collect(Collectors.groupingBy(tag -> tag.getService().getId()));

        return results.stream()
                .filter(service -> youthPolicyFilter.isYouthRelevant(
                        service,
                        tagsByServiceId.getOrDefault(service.getId(), Collections.emptyList())
                ))
                .toList();
    }

    // Boolean Mode 검색어 구성: 공백 분리 후 각 단어에 + 접두사
    private String buildFulltextKeyword(String keyword) {
        String[] words = keyword.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append("+").append(word).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_SEARCH_LIMIT;
        return Math.min(size, MAX_SEARCH_LIMIT);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "RELEVANCE";
        String upper = sort.trim().toUpperCase();
        return switch (upper) {
            case "RELEVANCE", "VIEWS", "LATEST", "NAME" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String upper = status.trim().toUpperCase();
        return switch (upper) {
            case "ACTIVE", "UPCOMING", "CLOSED" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return null;
        String upper = sourceType.trim().toUpperCase();
        return switch (upper) {
            case "YOUTH", "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private String normalizeNullable(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
