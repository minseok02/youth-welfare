package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RawApiPayloadReadRepositoryImpl implements RawApiPayloadReadRepository {

    private final RawApiPayloadRepository rawApiPayloadRepository;

    @Override
    public Optional<RawApiPayload> findBySourceTypeAndSourceIdAndApiCategory(
            WelfareService.SourceType sourceType,
            String sourceId,
            RawApiPayload.ApiCategory apiCategory
    ) {
        return rawApiPayloadRepository.findBySourceTypeAndSourceIdAndApiCategory(sourceType, sourceId, apiCategory);
    }

    @Override
    public List<RawApiPayload> findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
            WelfareService.SourceType sourceType,
            RawApiPayload.ApiCategory apiCategory
    ) {
        return rawApiPayloadRepository.findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(sourceType, apiCategory);
    }
}
