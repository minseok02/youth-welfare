package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface DeferredNormalizedPolicySidecarCommandRepository {

    record TaxonomySidecarBatchEntry(
            Long serviceId,
            WelfareService.SourceType sourceType,
            NormalizedPolicyAggregate aggregate
    ) {
    }

    void upsertTaxonomySummary(WelfareService service, NormalizedPolicyAggregate aggregate);

    void replaceTaxonomySummarySlots(WelfareService service, NormalizedPolicyAggregate aggregate);

    void replaceTaxonomySidecarsBatch(List<TaxonomySidecarBatchEntry> entries);

    void replaceTaxonomyTerms(Long serviceId,
                              List<NormalizedPolicyAggregate.TaxonomyTerm> taxonomyTerms,
                              List<String> refreshScopeGroups,
                              List<String> refreshScopeSourceFields);

    void upsertMergedFacts(Long serviceId, List<NormalizedPolicyAggregate.Fact> mergedFacts);

    void replaceFactsByCodeSet(Long serviceId,
                               String factCodeSetKey,
                               List<NormalizedPolicyAggregate.Fact> facts);
}
