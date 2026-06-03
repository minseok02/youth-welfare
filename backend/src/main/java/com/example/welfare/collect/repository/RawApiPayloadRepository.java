package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface RawApiPayloadRepository extends JpaRepository<RawApiPayload, Long> {

    Optional<RawApiPayload> findBySourceTypeAndSourceIdAndApiCategory(
            WelfareService.SourceType sourceType,
            String sourceId,
            RawApiPayload.ApiCategory apiCategory
    );

    List<RawApiPayload> findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
            WelfareService.SourceType sourceType,
            RawApiPayload.ApiCategory apiCategory
    );

    @Modifying
    void deleteBySourceTypeAndSourceIdIn(
            WelfareService.SourceType sourceType,
            List<String> sourceIds
    );
}
