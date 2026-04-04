package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationLogRepository;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 발송 시점에 recommendation_logs 기록
 * 클릭 추적: ?log_id= 파라미터로 PolicyController에서 호출
 */
@Service
@RequiredArgsConstructor
public class RecommendationLogService {

    private final RecommendationLogRepository logRepository;

    @Transactional
    public List<RecommendationLog> logNotification(User user,
                                                    List<UserRecommendation> recommendations,
                                                    ScoreWeight weight) {
        List<RecommendationLog> logs = recommendations.stream()
                .map(rec -> RecommendationLog.builder()
                        .user(user)
                        .service(rec.getService())
                        .finalScore(rec.getFinalScore())
                        .ruleWeightUsed(weight.getRuleWeight())
                        .aiWeightUsed(weight.getAiWeight())
                        .isFallback(rec.getAiScore() == null)
                        .build())
                .toList();

        return logRepository.saveAll(logs);
    }

    @Transactional
    public void markClicked(Long logId) {
        RecommendationLog log = logRepository.findById(logId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        log.click();
    }
}
