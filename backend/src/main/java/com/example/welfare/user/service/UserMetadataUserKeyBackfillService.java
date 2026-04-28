package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMetadataUserKeyBackfillService {

    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;

    @Transactional
    public UserMetadataUserKeyBackfillResponse backfillMissingUserKeys() {
        int attributeMissingCount = userAttributeRepository.countMissingUserKeys();
        int priorityMissingCount = userPriorityRepository.countMissingUserKeys();

        int attributeUpdatedCount = userAttributeRepository.backfillMissingUserKeys();
        int priorityUpdatedCount = userPriorityRepository.backfillMissingUserKeys();
        int processedCount = attributeMissingCount + priorityMissingCount;
        int updatedRowCount = attributeUpdatedCount + priorityUpdatedCount;

        log.info("[UserMetadataUserKeyBackfillService] user metadata user_key 백필 완료 processed={} updated={} attrs={} priorities={}",
                processedCount, updatedRowCount, attributeUpdatedCount, priorityUpdatedCount);

        return new UserMetadataUserKeyBackfillResponse(
                processedCount,
                updatedRowCount,
                attributeUpdatedCount,
                priorityUpdatedCount
        );
    }
}
