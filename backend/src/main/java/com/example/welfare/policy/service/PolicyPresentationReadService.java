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
        List<PolicySummaryResponse> responses = buildSummaryResponses(userId, page.getContent());
        Map<Long, PolicySummaryResponse> responseById = responses.stream()
                .collect(Collectors.toMap(
                        PolicySummaryResponse::getId,
                        response -> response,
                        (left, right) -> left
                ));
        return page.map(service -> responseById.getOrDefault(service.getId(), PolicySummaryResponse.from(service, false)));
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> buildSummaryResponses(Long userId, List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return List.of();
        }

        Set<Long> bookmarkedServiceIds = recommendationBookmarkReadService.findBookmarkedServiceIds(userId, services);
        Map<Long, RecommendationCandidateProjection> projections = findProjections(services);
        Map<Long, String> sidoMap = buildSidoMap(services);

        return services.stream()
                .map(service -> PolicySummaryResponse.from(
                        service,
                        bookmarkedServiceIds.contains(service.getId()),
                        projections.get(service.getId()),
                        sidoMap.get(service.getId())
                ))
                .toList();
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
