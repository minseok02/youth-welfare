package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.policy.entity.WelfareService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DefaultPriorityMatcher implements PriorityMatcher {

    @Override
    public boolean matches(PriorityPreference priority, WelfareService service) {
        return matches(priority, service, null);
    }

    @Override
    public boolean matches(PriorityPreference priority,
                           WelfareService service,
                           RecommendationCandidateProjection projection) {
        String code = priority.code();
        String unifiedCategory = resolveUnifiedCategory(service, projection);
        return switch (code) {
            case "HOUSING"       -> "주거".equals(unifiedCategory);
            case "JOB"           -> "일자리".equals(unifiedCategory);
            case "EDUCATION"     -> "교육·직업훈련".equals(unifiedCategory);
            case "FINANCE"       -> "금융·생활지원".equals(unifiedCategory);
            case "CULTURE"       -> "문화·여가".equals(unifiedCategory);
            case "DEADLINE"      -> isDeadlineSoon(service, projection);
            case "PARTICIPATION" -> "참여·기회".equals(unifiedCategory);
            case "FAMILY"        -> "가족·돌봄".equals(unifiedCategory);
            default -> false;
        };
    }

    private String resolveUnifiedCategory(WelfareService service, RecommendationCandidateProjection projection) {
        if (projection != null && projection.unifiedCategoryCompat() != null) {
            return projection.unifiedCategoryCompat();
        }
        return service.getUnifiedCategory();
    }

    private boolean isDeadlineSoon(WelfareService service, RecommendationCandidateProjection projection) {
        LocalDate applyEndDate = projection != null && projection.applyEndDate() != null
                ? projection.applyEndDate()
                : service.getApplyEndDate();
        if (applyEndDate == null) return false;
        LocalDate today = LocalDate.now();
        return !applyEndDate.isBefore(today)
                && applyEndDate.isBefore(today.plusDays(7));
    }
}
