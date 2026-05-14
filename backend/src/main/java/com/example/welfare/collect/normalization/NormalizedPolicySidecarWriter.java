package com.example.welfare.collect.normalization;

import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface NormalizedPolicySidecarWriter {

    void upsert(WelfareService service, NormalizedPolicyAggregate aggregate);

    void replaceFactsByCodeSet(WelfareService service,
                               String factCodeSetKey,
                               List<NormalizedPolicyAggregate.Fact> facts);
}
