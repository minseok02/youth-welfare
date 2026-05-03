package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import org.springframework.stereotype.Service;

/**
 * 1차 운영 기준 군집 서비스.
 *
 * 현재 프로젝트는 "청년정책 통합포털 + 개인화 추천"을 기본 축으로 두고,
 * 추천 재사용도 군집 캐시보다 userKey 기준 개인 캐시를 우선한다.
 * 따라서 1차 운영에서는 적극적 군집 분할을 하지 않고 항상 youth_all만 반환한다.
 *
 * 나이대 × 소득분위 2D 군집은 사용자 규모와 hit-rate가 충분히 커졌을 때만
 * 다시 활성화할 2차 확장 포인트로 남긴다.
 */
@Service
public class ClusterService {

    public String assignCluster(RecommendationUserSnapshot user) {
        return "youth_all";
    }
}
