package com.example.welfare.collect.service;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarWriter;
import com.example.welfare.collect.repository.BokjiroDetailCommandRepository;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.PolicyLookupReadRepository;
import com.example.welfare.policy.service.PolicyEmbeddingRefreshRequestService;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectPolicyAggregateApplyService {

    private final BokjiroDetailCommandRepository bokjiroDetailCommandRepository;
    private final PolicyLookupReadRepository policyLookupReadRepository;
    private final SearchYouthRelevanceService searchYouthRelevanceService;
    private final NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;
    private final PolicyEmbeddingRefreshRequestService policyEmbeddingRefreshRequestService;
    private final BokjiroDetailPersistenceSupport detailPersistenceSupport = new BokjiroDetailPersistenceSupport();

    @Transactional
    public void applyCollectedItem(WelfareService service,
                                   NormalizedPolicyAggregate aggregate,
                                   List<ServiceTag> tags) {
        if (aggregate != null) {
            normalizedPolicySidecarWriter.upsert(service, aggregate);
        }
        searchYouthRelevanceService.refreshForService(service, tags != null ? tags : List.of());
        policyEmbeddingRefreshRequestService.request(service.getId());
    }

    @Transactional
    public void applyCollectedDetail(WelfareService service,
                                     WelfareServiceDetail existing,
                                     NormalizedPolicyAggregate aggregate) {
        WelfareService managedService = policyLookupReadRepository.findById(service.getId())
                .orElseThrow(() -> new IllegalArgumentException("정책을 찾을 수 없습니다. serviceId=" + service.getId()));
        bokjiroDetailCommandRepository.save(detailPersistenceSupport.mergeDetail(managedService, existing, aggregate));
        detailPersistenceSupport.applyFallbacksToService(managedService, aggregate);
        normalizedPolicySidecarWriter.upsert(managedService, aggregate);
        searchYouthRelevanceService.refreshForService(managedService);
        policyEmbeddingRefreshRequestService.request(managedService.getId());
    }

    @Transactional
    public void applySidecarBackfill(WelfareService service, NormalizedPolicyAggregate aggregate) {
        normalizedPolicySidecarWriter.upsert(service, aggregate);
    }
}
