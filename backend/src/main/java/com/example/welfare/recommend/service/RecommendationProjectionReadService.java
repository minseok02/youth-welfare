package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationSummaryReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationProjectionReadService {

    private final RecommendationSummaryReadRepository recommendationSummaryReadRepository;

    @Transactional(readOnly = true)
    public Map<Long, RecommendationCandidateProjection> findCandidateProjections(List<UserRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return Map.of();
        }
        List<Long> serviceIds = recommendations.stream()
                .map(rec -> rec.getService().getId())
                .toList();
        return findCandidateProjectionsByServiceIds(serviceIds);
    }

    @Transactional(readOnly = true)
    public Map<Long, RecommendationCandidateProjection> findCandidateProjectionsByServices(List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return Map.of();
        }
        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();
        return findCandidateProjectionsByServiceIds(serviceIds);
    }

    @Transactional(readOnly = true)
    public Map<Long, RecommendationCandidateProjection> findCandidateProjectionsByServiceIds(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Map.of();
        }
        return recommendationSummaryReadRepository.findCandidateProjections(serviceIds);
    }
}
