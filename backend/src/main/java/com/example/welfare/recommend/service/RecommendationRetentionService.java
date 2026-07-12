package com.example.welfare.recommend.service;

import com.example.welfare.global.service.AppSchedulerGate;
import com.example.welfare.recommend.repository.RecommendationPersistenceCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationRetentionService {

    private final RecommendationPersistenceCommandRepository recommendationPersistenceCommandRepository;
    private final AppSchedulerGate appSchedulerGate;

    @Transactional
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void cleanupOldUnbookmarkedRecommendations() {
        if (!appSchedulerGate.shouldRun("RecommendationRetentionService.cleanupOldUnbookmarkedRecommendations")) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(30);
        recommendationPersistenceCommandRepository.deleteOldUnbookmarked(before);
        log.info("[RecommendationRetentionService] 30일 경과 미북마크 추천 정리 완료 before={}", before);
    }
}
