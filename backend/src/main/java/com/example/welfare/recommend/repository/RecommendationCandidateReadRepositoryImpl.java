package com.example.welfare.recommend.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.repository.PolicyTagReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RecommendationCandidateReadRepositoryImpl implements RecommendationCandidateReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;
    private final PolicyTagReadRepository policyTagReadRepository;

    @Override
    public List<WelfareService> findBaseCandidates(RecommendationCandidateReadCondition condition) {
        if (StringUtils.hasText(condition.regionCode())) {
            return welfareServiceRepository.findCandidatesWithRegionCode(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.regionCode().trim(),
                    PageRequest.of(0, condition.baseFetchSize())
            );
        }
        if (condition.sido() != null) {
            return welfareServiceRepository.findCandidatesWithSido(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.sido(),
                    PageRequest.of(0, condition.baseFetchSize())
            );
        }
        return welfareServiceRepository.findCandidates(
                condition.age(),
                condition.incomeLevel(),
                PageRequest.of(0, condition.baseFetchSize())
        );
    }

    @Override
    public List<WelfareService> findLatestCandidates(RecommendationCandidateReadCondition condition) {
        if (StringUtils.hasText(condition.regionCode())) {
            return welfareServiceRepository.findLatestCandidatesWithRegionCode(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.regionCode().trim(),
                    PageRequest.of(0, condition.latestFetchSize())
            );
        }
        if (condition.sido() != null) {
            return welfareServiceRepository.findLatestCandidatesWithSido(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.sido(),
                    PageRequest.of(0, condition.latestFetchSize())
            );
        }
        return welfareServiceRepository.findLatestCandidates(
                condition.age(),
                condition.incomeLevel(),
                PageRequest.of(0, condition.latestFetchSize())
        );
    }

    @Override
    public Map<Long, List<ServiceTag>> findTagsByServiceIds(List<Long> serviceIds) {
        return policyTagReadRepository.findByServiceIds(serviceIds);
    }
}
