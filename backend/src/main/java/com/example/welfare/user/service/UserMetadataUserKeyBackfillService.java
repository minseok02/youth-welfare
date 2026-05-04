package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.repository.UserMetadataBackfillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMetadataUserKeyBackfillService {

    private final UserMetadataBackfillRepository userMetadataBackfillRepository;

    @Transactional
    public UserMetadataUserKeyBackfillResponse backfillMissingUserKeys() {
        UserMetadataBackfillRepository.BackfillCounts missingCounts =
                userMetadataBackfillRepository.countMissingUserKeys();
        UserMetadataBackfillRepository.BackfillCounts updatedCounts =
                userMetadataBackfillRepository.backfillMissingUserKeys();

        int processedCount = missingCounts.attributeCount() + missingCounts.priorityCount();
        int updatedRowCount = updatedCounts.attributeCount() + updatedCounts.priorityCount();

        log.info("[UserMetadataUserKeyBackfillService] user metadata user_key 백필 완료 processed={} updated={} attrs={} priorities={}",
                processedCount, updatedRowCount, updatedCounts.attributeCount(), updatedCounts.priorityCount());

        return new UserMetadataUserKeyBackfillResponse(
                processedCount,
                updatedRowCount,
                updatedCounts.attributeCount(),
                updatedCounts.priorityCount()
        );
    }
}
