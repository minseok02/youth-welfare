package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import org.springframework.stereotype.Service;

/**
 * 1차: 항상 "youth_all" 반환
 * 2차: 나이대 × 소득분위 2D 군집화 (user_clusters 테이블 의존) — 현재 미구현
 */
@Service
public class ClusterService {

    public String assignCluster(RecommendationUserSnapshot user) {
        return "youth_all";
    }
}
