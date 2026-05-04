package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationResultReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationBookmarkStateReadService {

    private final RecommendationResultReadRepository recommendationResultReadRepository;

    @Transactional(readOnly = true)
    public Map<Long, Boolean> findLatestBookmarkStateByServiceId(String userKey) {
        return recommendationResultReadRepository.findLatestRecommendationRows(userKey)
                .stream()
                .collect(Collectors.toMap(
                        rec -> rec.getService().getId(),
                        UserRecommendation::isBookmarked,
                        (left, right) -> left
                ));
    }
}
