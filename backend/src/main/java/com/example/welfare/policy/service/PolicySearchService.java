package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicySearchService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;

    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> search(String keyword, int page) {
        // MySQL FULLTEXT 검색 (ngram 파서)
        // keyword는 Controller에서 trim 처리 후 전달됨
        String ftKeyword = buildFulltextKeyword(keyword);
        int offset = page * DEFAULT_SEARCH_LIMIT;

        List<WelfareService> results = welfareServiceRepository.searchByKeyword(
                ftKeyword, DEFAULT_SEARCH_LIMIT, offset);

        return results.stream()
                .map(PolicySummaryResponse::from)
                .collect(Collectors.toList());
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
}
