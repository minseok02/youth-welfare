package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.user.entity.UserPriority;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DefaultPriorityMatcher implements PriorityMatcher {

    @Override
    public boolean matches(UserPriority priority, WelfareService service) {
        String code = priority.getPriorityOption().getCode();
        return switch (code) {
            case "HOUSING"       -> "주거".equals(service.getUnifiedCategory());
            case "JOB"           -> "일자리".equals(service.getUnifiedCategory());
            case "EDUCATION"     -> "교육·직업훈련".equals(service.getUnifiedCategory());
            case "FINANCE"       -> "금융·생활지원".equals(service.getUnifiedCategory());
            case "CULTURE"       -> "문화·여가".equals(service.getUnifiedCategory());
            case "DEADLINE"      -> isDeadlineSoon(service);
            case "PARTICIPATION" -> "참여·기회".equals(service.getUnifiedCategory());
            case "FAMILY"        -> "가족·돌봄".equals(service.getUnifiedCategory());
            default -> false;
        };
    }

    private boolean isDeadlineSoon(WelfareService service) {
        if (service.getApplyEndDate() == null) return false;
        LocalDate today = LocalDate.now();
        return !service.getApplyEndDate().isBefore(today)
                && service.getApplyEndDate().isBefore(today.plusDays(7));
    }
}
