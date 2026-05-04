package com.example.welfare.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserProfileReadRepositoryImpl implements UserProfileReadRepository {

    private final UserProfileRepository userProfileRepository;
    private final UserPiiReadWriteRepository userPiiReadWriteRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;

    @Override
    public Optional<UserProfileAggregateReadModel> findProfileAggregateByUserKey(String userKey) {
        return userProfileRepository.findByUserKey(userKey)
                .flatMap(profile -> userPiiReadWriteRepository.findByUserKey(userKey)
                        .map(pii -> new UserProfileAggregateReadModel(
                                profile,
                                pii,
                                userAttributeRepository.findReadModelsByUserKey(userKey),
                                userPriorityRepository.findReadModelsByUserKey(userKey)
                        )));
    }
}
