package com.example.welfare.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserMetadataBackfillRepositoryImpl implements UserMetadataBackfillRepository {

    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;

    @Override
    public BackfillCounts countMissingUserKeys() {
        return new BackfillCounts(
                userAttributeRepository.countMissingUserKeys(),
                userPriorityRepository.countMissingUserKeys()
        );
    }

    @Override
    public BackfillCounts backfillMissingUserKeys() {
        return new BackfillCounts(
                userAttributeRepository.backfillMissingUserKeys(),
                userPriorityRepository.backfillMissingUserKeys()
        );
    }
}
