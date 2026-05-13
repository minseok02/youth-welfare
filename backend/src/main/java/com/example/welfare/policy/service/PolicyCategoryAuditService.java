package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import com.example.welfare.policy.repository.PolicyCategoryAuditReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyCategoryAuditService {

    private final PolicyCategoryAuditReadRepository policyCategoryAuditReadRepository;

    public PolicyCategoryAuditResponse readAudit() {
        long totalPolicyCount = policyCategoryAuditReadRepository.fetchTotalPolicyCount();
        long searchablePolicyCount = policyCategoryAuditReadRepository.fetchSearchablePolicyCount();
        List<PolicyCategoryAuditResponse.CategoryCount> unifiedCategoryCounts =
                policyCategoryAuditReadRepository.fetchUnifiedCategoryCounts();
        List<PolicyCategoryAuditResponse.SourceCategoryMappingCount> youthBroadCategoryMappings =
                policyCategoryAuditReadRepository.fetchYouthBroadCategoryMappings();
        return new PolicyCategoryAuditResponse(
                totalPolicyCount,
                searchablePolicyCount,
                ratio(searchablePolicyCount, totalPolicyCount),
                unifiedCategoryCounts,
                buildTopUnifiedCategorySummaries(unifiedCategoryCounts, totalPolicyCount),
                youthBroadCategoryMappings,
                buildSourceCategorySummaries(youthBroadCategoryMappings)
        );
    }

    private List<PolicyCategoryAuditResponse.CategorySummary> buildTopUnifiedCategorySummaries(
            List<PolicyCategoryAuditResponse.CategoryCount> counts,
            long totalPolicyCount
    ) {
        return counts.stream()
                .sorted(Comparator.comparingLong(PolicyCategoryAuditResponse.CategoryCount::searchableCount).reversed()
                        .thenComparing(Comparator.comparingLong(PolicyCategoryAuditResponse.CategoryCount::totalCount).reversed())
                        .thenComparing(PolicyCategoryAuditResponse.CategoryCount::unifiedCategory))
                .limit(5)
                .map(count -> new PolicyCategoryAuditResponse.CategorySummary(
                        count.unifiedCategory(),
                        count.totalCount(),
                        count.searchableCount(),
                        ratio(count.totalCount(), totalPolicyCount),
                        ratio(count.searchableCount(), count.totalCount())
                ))
                .toList();
    }

    private List<PolicyCategoryAuditResponse.SourceCategorySummary> buildSourceCategorySummaries(
            List<PolicyCategoryAuditResponse.SourceCategoryMappingCount> mappings
    ) {
        Map<String, List<PolicyCategoryAuditResponse.SourceCategoryMappingCount>> grouped =
                mappings.stream()
                        .collect(Collectors.groupingBy(
                                PolicyCategoryAuditResponse.SourceCategoryMappingCount::sourceCategory,
                                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                        .sorted(Comparator.comparingLong(PolicyCategoryAuditResponse.SourceCategoryMappingCount::totalCount).reversed()
                                                .thenComparing(PolicyCategoryAuditResponse.SourceCategoryMappingCount::unifiedCategory))
                                        .toList())
                        ));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<PolicyCategoryAuditResponse.SourceCategoryMappingCount> groupedMappings = entry.getValue();
                    long totalCount = groupedMappings.stream()
                            .mapToLong(PolicyCategoryAuditResponse.SourceCategoryMappingCount::totalCount)
                            .sum();
                    PolicyCategoryAuditResponse.SourceCategoryMappingCount dominant = groupedMappings.get(0);
                    return new PolicyCategoryAuditResponse.SourceCategorySummary(
                            entry.getKey(),
                            totalCount,
                            dominant.unifiedCategory(),
                            dominant.totalCount(),
                            ratio(dominant.totalCount(), totalCount),
                            groupedMappings
                    );
                })
                .toList();
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (double) numerator / denominator;
    }
}
