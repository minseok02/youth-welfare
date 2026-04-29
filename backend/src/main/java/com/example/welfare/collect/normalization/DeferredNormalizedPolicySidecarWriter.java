package com.example.welfare.collect.normalization;

import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * sidecar 테이블이 아직 없으므로 collect 경로에서는 canonical aggregate 전달만 연결하고,
 * facts merge 계약은 runtime 에서 미리 검증한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeferredNormalizedPolicySidecarWriter implements NormalizedPolicySidecarWriter {

    private final NormalizedFactMergeSupport normalizedFactMergeSupport;

    @Override
    public void upsert(WelfareService service, NormalizedPolicyAggregate aggregate) {
        if (service == null || aggregate == null || aggregate.core() == null) {
            throw new IllegalArgumentException("service/aggregate/core 는 필수입니다.");
        }

        List<NormalizedPolicyAggregate.Fact> mergedFacts =
                normalizedFactMergeSupport.merge(List.of(), aggregate.facts());

        log.debug("[DeferredNormalizedPolicySidecarWriter] deferred sidecar upsert serviceId={} sourceType={} facts={} taxonomyTerms={}",
                service.getId(),
                service.getSourceType(),
                mergedFacts.size(),
                aggregate.taxonomyTerms().size());
    }
}
