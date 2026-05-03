package com.example.welfare.policy.repository;

import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WelfareServiceReadRepositoryImpl implements WelfareServiceReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public Page<WelfareService> findList(PolicyListReadCondition condition, Pageable pageable) {
        String sido = condition.sido();
        // 온통청년은 sido_name/sgg_name이 NULL이고 region_code만 저장되므로 행정코드 경로도 함께 전달
        String sidoCode = RegionCodeUtil.getSidoCode(sido);
        String regionCode = RegionCodeUtil.getRegionCode(sido, condition.sgg());
        return welfareServiceRepository.findListWithFilters(
                condition.category(),
                condition.sourceType(),
                condition.status(),
                condition.statusFilter(),
                sido,
                condition.sgg(),
                sidoCode,
                regionCode,
                condition.onlineApply(),
                pageable
        );
    }

    @Override
    public Page<WelfareService> search(PolicySearchReadCondition condition, Pageable pageable) {
        if (condition.sido() == null) {
            return welfareServiceRepository.searchByKeywordWithFiltersNoRegion(
                    condition.keyword(),
                    condition.status(),
                    condition.statusFilter(),
                    condition.category(),
                    condition.sourceType(),
                    condition.onlineApply(),
                    condition.sort(),
                    pageable
            );
        }
        if (condition.sgg() == null) {
            return welfareServiceRepository.searchByKeywordWithFiltersWithSido(
                    condition.keyword(),
                    condition.status(),
                    condition.statusFilter(),
                    condition.category(),
                    condition.sourceType(),
                    condition.onlineApply(),
                    condition.sido(),
                    condition.sort(),
                    pageable
            );
        }
        return welfareServiceRepository.searchByKeywordWithFiltersWithSidoSgg(
                condition.keyword(),
                condition.status(),
                condition.statusFilter(),
                condition.category(),
                condition.sourceType(),
                condition.onlineApply(),
                condition.sido(),
                condition.sgg(),
                condition.sort(),
                pageable
        );
    }
}
