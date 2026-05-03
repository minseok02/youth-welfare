package com.example.welfare.recommend.facade;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationReadFacade {

    private final UserRecommendationRepository userRecommendationRepository;
    private final UserRepository userRepository;

    public Set<Long> findBookmarkedServiceIds(Long userId, List<WelfareService> services) {
        if (userId == null || services == null || services.isEmpty()) {
            return Collections.emptySet();
        }
        String userKey = userRepository.findUserKeyById(userId).orElse(null);
        if (userKey == null) {
            return Collections.emptySet();
        }

        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();

        return new HashSet<>(userRecommendationRepository.findLatestBookmarkedServiceIdsByUserKey(userKey, serviceIds));
    }
}
