package com.example.welfare.policy.repository;

import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WelfareServiceReadRepositoryImpl implements WelfareServiceReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceSearchRepository welfareServiceSearchRepository;

    @Override
    public Page<WelfareService> findList(PolicyListReadCondition condition, Pageable pageable) {
        String sido = RegionCodeUtil.fullSidoName(condition.sido());
        String sidoCode = RegionCodeUtil.getSidoCode(sido);
        String regionCode = RegionCodeUtil.getRegionCode(sido, condition.sgg());
        Integer onlineApplyInt = condition.onlineApply() == null ? null : (condition.onlineApply() ? 1 : 0);
        // native query handles ORDER BY — pass unsorted pageable for page/size only
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (shouldUseActiveOnlyListFastPath(condition, sido, regionCode, onlineApplyInt)) {
            return findActiveOnlyList(condition.sort(), unsorted);
        }
        return welfareServiceRepository.findListWithFilters(
                condition.category(),
                condition.sourceType() != null ? condition.sourceType().name() : null,
                condition.status() != null ? condition.status().name() : null,
                condition.statusFilter(),
                sido,
                condition.sgg(),
                sidoCode,
                regionCode,
                onlineApplyInt,
                condition.sort(),
                condition.incomeMaxWon(),
                condition.targetGroup(),
                condition.gov24ServiceField(),
                condition.gov24UserType(),
                condition.gov24BenefitType(),
                unsorted
        );
    }

    @Override
    public Page<WelfareService> search(PolicySearchReadCondition condition, Pageable pageable) {
        return welfareServiceSearchRepository.search(condition, pageable);
    }

    private boolean shouldUseActiveOnlyListFastPath(PolicyListReadCondition condition,
                                                   String sido,
                                                   String regionCode,
                                                   Integer onlineApplyInt) {
        return condition.status() == null
                && "ACTIVE_ONLY".equals(condition.statusFilter())
                && condition.category() == null
                && condition.sourceType() == null
                && sido == null
                && condition.sgg() == null
                && regionCode == null
                && onlineApplyInt == null
                && condition.incomeMaxWon() == null
                && condition.targetGroup() == null
                && condition.gov24ServiceField() == null
                && condition.gov24UserType() == null
                && condition.gov24BenefitType() == null
                && isFastPathSort(condition.sort());
    }

    private boolean isFastPathSort(String sort) {
        return "LATEST".equals(sort) || "DEADLINE".equals(sort) || "VIEWS".equals(sort);
    }

    private Page<WelfareService> findActiveOnlyList(String sort, Pageable pageable) {
        return switch (sort) {
            case "DEADLINE" -> welfareServiceRepository.findActiveOnlyDeadlineList(pageable);
            case "VIEWS" -> welfareServiceRepository.findActiveOnlyViewsList(pageable);
            case "LATEST" -> welfareServiceRepository.findActiveOnlyLatestList(pageable);
            default -> throw new IllegalArgumentException("Unsupported active list fast-path sort: " + sort);
        };
    }
}
