package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NormalizedPolicySidecarBackfillReadRepositoryImpl implements NormalizedPolicySidecarBackfillReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public Optional<WelfareService> findServiceBySourceTypeAndSourceId(WelfareService.SourceType sourceType, String sourceId) {
        return welfareServiceRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
    }
}
