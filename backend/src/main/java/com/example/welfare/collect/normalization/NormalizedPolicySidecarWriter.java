package com.example.welfare.collect.normalization;

import com.example.welfare.policy.entity.WelfareService;

public interface NormalizedPolicySidecarWriter {

    void upsert(WelfareService service, NormalizedPolicyAggregate aggregate);
}
