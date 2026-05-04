package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;

public interface DeferredNormalizedPolicySidecarCommandRepository {

    void upsertTaxonomySummary(WelfareService service, NormalizedPolicyAggregate aggregate);

    void replaceTaxonomySummarySlots(WelfareService service, NormalizedPolicyAggregate aggregate);
}
