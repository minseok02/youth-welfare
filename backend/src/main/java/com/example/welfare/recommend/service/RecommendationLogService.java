package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationLogRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알림 발송 시점에 recommendation_logs 기록
 * 클릭 추적: ?log_id= 파라미터로 PolicyController에서 호출
 */
@Service
@RequiredArgsConstructor
public class RecommendationLogService {

    private final RecommendationLogRepository logRepository;
    private final UserRepository userRepository;

    // refresh 시점 — 미클릭 이전 로그 제거 후 새 로그 생성
    @Transactional
    public List<RecommendationLog> refreshLogs(User user,
                                                List<UserRecommendation> recommendations,
                                                ScoreWeight weight) {
        logRepository.deleteUnclickedByUserKey(user.getUserKey());
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
                        .isFallback(rec.getAiScore() == null)
                        .build())
                .toList();

        return logRepository.saveAll(logs);
    }

    // serviceId 목록 기준 사용자의 최신 logId 맵 반환 (추천 refresh 응답 조합용)
    @Transactional(readOnly = true)
    public Map<Long, Long> findLatestLogIdMap(Long userId, List<Long> serviceIds) {
        if (serviceIds.isEmpty()) return Map.of();
        String userKey = userRepository.findUserKeyById(userId).orElse(null);
        if (userKey == null) return Map.of();
        return logRepository.findLatestByUserKeyAndServiceIds(userKey, serviceIds)
                .stream()
                .collect(Collectors.toMap(
                        log -> log.getService().getId(),
                        RecommendationLog::getId,
                        (a, b) -> b
                ));
    }

    @Transactional
    public void markClicked(Long logId) {
        RecommendationLog log = logRepository.findById(logId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        log.click();
    }
}
