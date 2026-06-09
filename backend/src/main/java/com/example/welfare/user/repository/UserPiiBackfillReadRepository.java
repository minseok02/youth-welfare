package com.example.welfare.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserPiiBackfillReadRepository {

    List<UserPiiBackfillStateReadModel> findMissingEncryptedFields();

    List<UserPiiReadModel> findLegacyEncryptedFields();

    Map<String, UserLegacyPiiSourceReadModel> findLegacySourceByUserKeys(Collection<String> userKeys);
}
