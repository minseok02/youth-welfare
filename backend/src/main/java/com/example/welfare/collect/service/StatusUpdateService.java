package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.StatusUpdateReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.repository.ClusterAiResultCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusUpdateService {

    private final StatusUpdateReadRepository statusUpdateReadRepository;
    private final ClusterAiResultCommandRepository clusterAiResultCommandRepository;

    /**
     * 매일 새벽 3시 — 종료된 정책 CLOSED 처리 + CLOSED 정책 후처리
     * CLAUDE.md: CLOSED 정책 ai_score → NULL 리셋 (user_recommendations의 ai_score는 RecommendationPersistenceService에서 처리)
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void updateStatuses() {
        log.info("[StatusUpdateService] 정책 상태 업데이트 시작");

        LocalDate today = LocalDate.now();
        int closedCount = 0;
        int activatedCount = 0;

        // ACTIVE → CLOSED: endDate 또는 applyEndDate가 어제 이전
        List<WelfareService> actives = statusUpdateReadRepository.findActiveServices();
        for (WelfareService ws : actives) {
            if (isClosed(ws, today)) {
                ws.updateStatus(WelfareService.ServiceStatus.CLOSED);
                closedCount++;
            }
        }

        // UPCOMING → ACTIVE: startDate 또는 applyStartDate가 오늘 이전
        List<WelfareService> upcomings = statusUpdateReadRepository.findUpcomingServices();
        for (WelfareService ws : upcomings) {
            if (isNowActive(ws, today)) {
                ws.updateStatus(WelfareService.ServiceStatus.ACTIVE);
                activatedCount++;
            }
        }

        log.info("[StatusUpdateService] 완료 — CLOSED: {}건, ACTIVE 전환: {}건", closedCount, activatedCount);

        // 군집 AI 캐시 TTL 정리 — 25시간 이상된 캐시 삭제 (매일 수집 주기에 맞춤)
        clusterAiResultCommandRepository.deleteExpiredBefore(LocalDateTime.now().minusHours(25));
        log.info("[StatusUpdateService] 군집 AI 캐시 만료 항목 정리 완료");
    }

    private boolean isClosed(WelfareService ws, LocalDate today) {
        // 사업 종료일 기준
        if (ws.getEndDate() != null && ws.getEndDate().isBefore(today)) {
            return true;
        }
        // 신청 종료일 기준 (사업 종료일 없는 경우)
        if (ws.getEndDate() == null && ws.getApplyEndDate() != null
                && ws.getApplyEndDate().isBefore(today)) {
            return true;
        }
        return false;
    }

    private boolean isNowActive(WelfareService ws, LocalDate today) {
        if (ws.getStartDate() != null && !ws.getStartDate().isAfter(today)) {
            return true;
        }
        if (ws.getStartDate() == null && ws.getApplyStartDate() != null
                && !ws.getApplyStartDate().isAfter(today)) {
            return true;
        }
        return false;
    }
}
