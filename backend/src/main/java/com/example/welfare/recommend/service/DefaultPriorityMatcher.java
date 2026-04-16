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
            case "HOUSING" -> "주거".equals(service.getUnifiedCategory());
            case "AMOUNT" -> "금융·생활지원".equals(service.getUnifiedCategory());
            case "ONLINE" -> Boolean.TRUE.equals(service.getIsOnlineApply());
            case "YOUTH_ONLY" -> service.getSourceType() == WelfareService.SourceType.YOUTH;
            case "EDU_JOB" -> "교육·직업훈련".equals(service.getUnifiedCategory())
                    || "일자리".equals(service.getUnifiedCategory());
            case "CULTURE" -> "문화·여가".equals(service.getUnifiedCategory());
            case "DEADLINE" -> isDeadlineSoon(service);
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

