package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceDetailRepository detailRepository;
    private final ServiceRegionRepository regionRepository;
    private final ServiceTagRepository tagRepository;

    @Transactional(readOnly = true)
    public Page<PolicySummaryResponse> getList(String category, Pageable pageable) {
        List<WelfareService.ServiceStatus> activeStatuses =
                List.of(WelfareService.ServiceStatus.ACTIVE, WelfareService.ServiceStatus.UPCOMING);

        Page<WelfareService> page;
        if (category != null && !category.isBlank()) {
            page = welfareServiceRepository.findByUnifiedCategoryAndStatusIn(
                    category, activeStatuses, pageable);
        } else {
            page = welfareServiceRepository.findAll(pageable);
        }

        return page.map(PolicySummaryResponse::from);
    }

    @Transactional
    public PolicyDetailResponse getDetail(Long serviceId) {
        WelfareService ws = welfareServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
        ws.increaseViewCount();

        WelfareServiceDetail detail = detailRepository.findByServiceId(serviceId).orElse(null);
        List<ServiceRegion> regions = regionRepository.findByServiceId(serviceId);
        List<ServiceTag> tags = tagRepository.findByServiceId(serviceId);

        return PolicyDetailResponse.of(ws, detail, regions, tags);
    }
}
