package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyDetailService {

    private final PolicyLookupService policyLookupService;
    private final PolicyDetailReadService policyDetailReadService;
    private final PolicyPresentationReadService policyPresentationReadService;

    @Transactional
    public PolicyDetailResponse getDetail(Long userId, Long serviceId, boolean increaseViewCount) {
        WelfareService service = policyLookupService.getRequiredService(serviceId);
        if (increaseViewCount) {
            service.increaseViewCount();
        }

        PolicyDetailReadService.PolicyDetailAggregate detailAggregate = policyDetailReadService.getAggregate(serviceId);
        PolicyPresentationReadService.PolicyDetailPresentation presentation =
                policyPresentationReadService.buildDetailPresentation(userId, service);

        return PolicyDetailResponse.of(
                service,
                detailAggregate.detail(),
                detailAggregate.regions(),
                detailAggregate.tags(),
                presentation.bookmarked(),
                presentation.projection()
        );
    }
}
