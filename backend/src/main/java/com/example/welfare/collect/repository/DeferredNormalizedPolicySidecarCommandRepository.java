package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface DeferredNormalizedPolicySidecarCommandRepository {

    void upsertTaxonomySummary(WelfareService service, NormalizedPolicyAggregate aggregate);

    void replaceTaxonomySummarySlots(WelfareService service, NormalizedPolicyAggregate aggregate);

    void replaceTaxonomyTerms(Long serviceId,
                              List<NormalizedPolicyAggregate.TaxonomyTerm> taxonomyTerms,
                              List<String> refreshScopeGroups,
                              List<String> refreshScopeSourceFields);

    void upsertMergedFacts(Long serviceId, List<NormalizedPolicyAggregate.Fact> mergedFacts);
}
