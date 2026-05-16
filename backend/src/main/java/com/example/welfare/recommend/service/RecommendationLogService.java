package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.entity.AiScoreStatus;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationLogCommandRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserKeyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 알림 발송 시점에 recommendation_logs 기록
 * 클릭 추적: ?log_id= 파라미터로 PolicyController에서 호출
 */
@Service
@RequiredArgsConstructor
public class RecommendationLogService {

    private final RecommendationLogCommandRepository recommendationLogCommandRepository;
    private final UserKeyLookupService userKeyLookupService;

    // refresh 시점 — 미클릭 이전 로그 제거 후 새 로그 생성
    @Transactional
    public List<RecommendationLog> refreshLogs(User user,
                                                List<UserRecommendation> recommendations,
                                                ScoreWeight weight) {
        recommendationLogCommandRepository.deleteUnclickedByUserKey(user.getUserKey());
        return logNotification(user, recommendations, weight);
    }

    @Transactional
    public List<RecommendationLog> logNotification(User user,
                                                    List<UserRecommendation> recommendations,
                                                    ScoreWeight weight) {
        List<RecommendationLog> logs = recommendations.stream()
                .map(rec -> RecommendationLog.builder()
                        .userKey(user.getUserKey())
                        .service(rec.getService())
                        .finalScore(rec.getFinalScore())
                        .ruleWeightUsed(weight.getRuleWeight())
                        .aiWeightUsed(weight.getAiWeight())
                        .isFallback(rec.getAiStatus() != AiScoreStatus.SCORED)
                        .build())
                .toList();

        return recommendationLogCommandRepository.saveAll(logs);
    }

    @Transactional
    public void markClicked(Long logId, Long userId) {
        String userKey = userKeyLookupService.findNullable(userId);
        if (!StringUtils.hasText(userKey)) {
            return;
        }

        RecommendationLog log = recommendationLogCommandRepository.findByIdAndUserKey(logId, userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        log.click();
    }
}
