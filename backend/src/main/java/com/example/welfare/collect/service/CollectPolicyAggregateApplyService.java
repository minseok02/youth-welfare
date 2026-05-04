package com.example.welfare.collect.service;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarWriter;
import com.example.welfare.collect.repository.BokjiroDetailCommandRepository;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectPolicyAggregateApplyService {

    private final BokjiroDetailCommandRepository bokjiroDetailCommandRepository;
    private final SearchYouthRelevanceService searchYouthRelevanceService;
    private final NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;
    private final BokjiroDetailPersistenceSupport detailPersistenceSupport = new BokjiroDetailPersistenceSupport();

    public void applyCollectedItem(WelfareService service,
                                   NormalizedPolicyAggregate aggregate,
                                   List<ServiceTag> tags) {
        if (aggregate != null) {
            normalizedPolicySidecarWriter.upsert(service, aggregate);
        }
        searchYouthRelevanceService.refreshForService(service, tags != null ? tags : List.of());
    }

    public void applyCollectedDetail(WelfareService service,
                                     WelfareServiceDetail existing,
                                     NormalizedPolicyAggregate aggregate) {
        bokjiroDetailCommandRepository.save(detailPersistenceSupport.mergeDetail(service, existing, aggregate));
        detailPersistenceSupport.applyFallbacksToService(service, aggregate);
        normalizedPolicySidecarWriter.upsert(service, aggregate);
        searchYouthRelevanceService.refreshForService(service);
    }

    public void applySidecarBackfill(WelfareService service, NormalizedPolicyAggregate aggregate) {
        normalizedPolicySidecarWriter.upsert(service, aggregate);
    }
}
