package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.service.RecommendationBookmarkReadService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyPresentationReadService {

    private final RecommendationBookmarkReadService recommendationBookmarkReadService;
    private final RecommendationProjectionReadService recommendationProjectionReadService;
    private final ServiceRegionRepository serviceRegionRepository;

    @Transactional(readOnly = true)
    public Page<PolicySummaryResponse> buildSummaryPage(Long userId, Page<WelfareService> page) {
        Set<Long> bookmarkedServiceIds = recommendationBookmarkReadService.findBookmarkedServiceIds(userId, page.getContent());
        Map<Long, RecommendationCandidateProjection> projections = findProjections(page.getContent());
        // 카드 source 표시: hostOrg 없는 복지로 지자체 정책에 sido 제공 (B안)
        Map<Long, String> sidoMap = buildSidoMap(page.getContent());
        return page.map(service -> PolicySummaryResponse.from(
                service,
                bookmarkedServiceIds.contains(service.getId()),
                projections.get(service.getId()),
                sidoMap.get(service.getId())
        ));
    }

    @Transactional(readOnly = true)
    public PolicyDetailPresentation buildDetailPresentation(Long userId, WelfareService service) {
        boolean bookmarked = recommendationBookmarkReadService.findBookmarkedServiceIds(userId, List.of(service))
                .contains(service.getId());
        RecommendationCandidateProjection projection = recommendationProjectionReadService
                .findCandidateProjectionsByServices(List.of(service))
                .get(service.getId());
        return new PolicyDetailPresentation(bookmarked, projection);
    }

    @Transactional(readOnly = true)
    public Map<Long, RecommendationCandidateProjection> findProjections(List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return Map.of();
        }
        return recommendationProjectionReadService.findCandidateProjectionsByServices(services);
    }

    // BOKJIRO_LOCAL만 sido_name이 있고, YOUTH는 region_code만 있어 sido_name=NULL → 이 맵에 안 잡힘
    private Map<Long, String> buildSidoMap(List<WelfareService> services) {
        if (services == null || services.isEmpty()) return Map.of();
        List<Long> ids = services.stream().map(WelfareService::getId).toList();
        return serviceRegionRepository.findFirstSidoByServiceIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> (String) row[1],
                        (a, b) -> a
                ));
    }

    public record PolicyDetailPresentation(
            boolean bookmarked,
            RecommendationCandidateProjection projection
    ) {
    }
}
