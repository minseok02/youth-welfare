package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface WelfareServiceSearchRepository {

    Page<WelfareService> search(PolicySearchReadCondition condition, Pageable pageable);

    List<WelfareService> searchChatCandidates(String keyword, int limit);

    List<WelfareService> searchSuggestionTitleCandidates(String keyword, int limit);
}
