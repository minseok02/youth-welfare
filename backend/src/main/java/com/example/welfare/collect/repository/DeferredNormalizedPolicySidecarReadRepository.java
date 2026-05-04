package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;

import java.util.List;

public interface DeferredNormalizedPolicySidecarReadRepository {

    boolean sidecarTablesReady();

    boolean summarySlotTableReady();

    List<NormalizedPolicyAggregate.Fact> findExistingFacts(Long serviceId);
}
