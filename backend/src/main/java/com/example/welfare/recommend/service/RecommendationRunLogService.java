package com.example.welfare.recommend.service;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.recommend.repository.RecommendationRunLogCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationRunLogService {

    private final RecommendationRunLogCommandRepository recommendationRunLogCommandRepository;

    public void record(RecommendationRunLogCommand command) {
        try {
            recommendationRunLogCommandRepository.save(command);
        } catch (RuntimeException e) {
            log.warn("[RecommendationRunLogService] recommendation run log save failed userKeyHash={} outcome={} errorType={}",
                    RedisKeyHash.sha256Hex(command.userKey()), command.outcome(), e.getClass().getSimpleName());
        }
    }
}
