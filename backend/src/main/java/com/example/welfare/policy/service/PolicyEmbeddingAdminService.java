package com.example.welfare.policy.service;

import com.example.welfare.chat.service.PolicyChunkEmbeddingService;
import com.example.welfare.policy.dto.PolicyEmbeddingRefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyEmbeddingAdminService {

    private final PolicyChunkEmbeddingService policyChunkEmbeddingService;

    public PolicyEmbeddingRefreshResponse rebuildSearchablePolicyEmbeddings() {
        PolicyChunkEmbeddingService.EmbeddingRefreshResult result =
                policyChunkEmbeddingService.refreshEmbeddingsForSearchablePolicies();
        return new PolicyEmbeddingRefreshResponse(
                "searchable",
                result.requestedServiceCount(),
                result.scannedChunkCount(),
                result.refreshedChunkCount()
        );
    }

    public PolicyEmbeddingRefreshResponse rebuildPolicyEmbeddings(List<Long> serviceIds) {
        PolicyChunkEmbeddingService.EmbeddingRefreshResult result =
                policyChunkEmbeddingService.refreshEmbeddingsForServiceIds(serviceIds);
        return new PolicyEmbeddingRefreshResponse(
                "service_ids",
                result.requestedServiceCount(),
                result.scannedChunkCount(),
                result.refreshedChunkCount()
        );
    }
}
