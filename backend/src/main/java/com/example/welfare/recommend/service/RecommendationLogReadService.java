package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.repository.RecommendationLogReadRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationLogReadService {

    private final RecommendationLogReadRepository recommendationLogReadRepository;
    private final UserKeyLookupService userKeyLookupService;

    @Transactional(readOnly = true)
    public Map<Long, Long> findLatestLogIdMap(Long userId, List<Long> serviceIds) {
        if (serviceIds.isEmpty()) return Map.of();
        String userKey = userKeyLookupService.findNullable(userId);
        if (userKey == null) return Map.of();
        return recommendationLogReadRepository.findLatestByUserKeyAndServiceIds(userKey, serviceIds)
                .stream()
                .collect(Collectors.toMap(
                        log -> log.getService().getId(),
                        RecommendationLog::getId,
                        (a, b) -> b
                ));
    }
}
