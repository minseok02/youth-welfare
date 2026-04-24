package com.example.welfare.recommend.service;

import com.example.welfare.user.entity.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 나이대 × 소득분위 2D 군집화
 * 나이: young(19-24) / mid(25-29) / senior(30-34)
 * 소득: low(1-3분위) / mid(4-6분위) / high(7-10분위)
 * → 9개 군집 + youth_all fallback
 */
@Service
public class ClusterService {

    public String assignCluster(User user) {
        String ageGroup = getAgeGroup(user);
        String incomeGroup = getIncomeGroup(user);

        if (ageGroup == null || incomeGroup == null) {
            return "youth_all";
        }
        return ageGroup + "_" + incomeGroup;
    }

    private String getAgeGroup(User user) {
        if (user.getBirthDate() == null) return null;
        int age = LocalDate.now().getYear() - user.getBirthDate().getYear();
        if (age < 19 || age > 34) return null;
        if (age <= 24) return "young";
        if (age <= 29) return "mid";
        return "senior";
    }

    private String getIncomeGroup(User user) {
        if (user.getIncomeLevel() == null) return null;
        int level = user.getIncomeLevel();
        if (level <= 3) return "low";
        if (level <= 6) return "mid";
        return "high";
    }
}
