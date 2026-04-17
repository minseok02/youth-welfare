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
            // onlineApply는 소스별 의미가 일관되지 않아 우선순위 가중치에 반영하지 않는다.
            case "ONLINE" -> false;
            // sourceType=YOUTH는 수집 출처일 뿐, 청년전용 여부를 직접 보장하지 않는다.
            case "YOUTH_ONLY" -> false;
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
