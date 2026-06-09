package com.example.welfare.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class UserPiiBackfillReadRepositoryImpl implements UserPiiBackfillReadRepository {

    private final UserRepository userRepository;
    private final UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Override
    public List<UserPiiBackfillStateReadModel> findMissingEncryptedFields() {
        return userPiiReadWriteRepository.findMissingEncryptedFields();
    }

    @Override
    public List<UserPiiReadModel> findLegacyEncryptedFields() {
        return userPiiReadWriteRepository.findLegacyEncryptedFields();
    }

    @Override
    public Map<String, UserLegacyPiiSourceReadModel> findLegacySourceByUserKeys(Collection<String> userKeys) {
        if (userKeys.isEmpty()) {
            return Map.of();
        }
        return userRepository.findPiiBackfillSourcesByUserKeys(userKeys).stream()
                .collect(LinkedHashMap::new,
                        (map, source) -> map.put(source.getUserKey(), source),
                        Map::putAll);
    }
}
