package com.example.welfare.user.repository;

public interface UserMetadataBackfillRepository {

    BackfillCounts countMissingUserKeys();

    BackfillCounts backfillMissingUserKeys();

    record BackfillCounts(
            int attributeCount,
            int priorityCount
    ) {
    }
}
