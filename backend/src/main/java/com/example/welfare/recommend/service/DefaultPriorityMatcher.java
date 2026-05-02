package com.example.welfare.recommend.service;

import com.example.welfare.policy.support.CompatCategorySupport;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.support.RecommendationRuntimeSupport;
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
        return switch (code) {
            case "HOUSING",
                 "JOB",
                 "EDUCATION",
                 "FINANCE",
                 "CULTURE",
                 "PARTICIPATION",
                 "FAMILY" -> matchesPriorityBucket(code, service, projection);
            case "DEADLINE"      -> isDeadlineSoon(service, projection);
            default -> false;
        };
    }

    private boolean matchesPriorityBucket(String priorityCode,
                                          WelfareService service,
                                          RecommendationCandidateProjection projection) {
        if (projection != null) {
            if (projection.priorityBuckets().contains(priorityCode)) {
                return true;
            }
            if (projection.compatPriorityBucket() != null) {
                return priorityCode.equals(projection.compatPriorityBucket());
            }
            return false;
        }
        return priorityCode.equals(CompatCategorySupport.priorityBucket(service.getUnifiedCategory()));
    }

    private boolean isDeadlineSoon(WelfareService service, RecommendationCandidateProjection projection) {
        LocalDate applyEndDate = projection != null && projection.applyEndDate() != null
                ? projection.applyEndDate()
                : service.getApplyEndDate();
        return RecommendationRuntimeSupport.isDeadlineSoon(applyEndDate, LocalDate.now());
    }
}
