package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RecommendationUserReadRepositoryImpl implements RecommendationUserReadRepository {

    private final UserProfileRepository userProfileRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;

    @Override
    public Optional<RecommendationUserReadModel> findByUserKey(String userKey) {
        return userProfileRepository.findByUserKey(userKey)
                .map(profile -> toReadModel(userKey, profile));
    }

    private RecommendationUserReadModel toReadModel(String userKey, UserProfile profile) {
        return new RecommendationUserReadModel(
                profile,
                userAttributeRepository.findReadModelsByUserKey(userKey),
                userPriorityRepository.findReadModelsByUserKey(userKey)
        );
    }
}
