package com.example.welfare.policy.repository;

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
        return welfareServiceRepository.findListWithFilters(
                condition.category(),
                condition.sourceType(),
                condition.status(),
                condition.includeClosed(),
                condition.sido(),
                condition.sgg(),
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
                    condition.includeClosed(),
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
                    condition.includeClosed(),
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
                condition.includeClosed(),
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
