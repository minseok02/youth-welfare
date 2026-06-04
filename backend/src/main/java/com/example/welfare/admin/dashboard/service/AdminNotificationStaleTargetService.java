package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleTargetResponse;
import com.example.welfare.admin.dashboard.repository.AdminNotificationStaleTargetReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminNotificationStaleTargetService {

    private final AdminNotificationStaleTargetReadRepository readRepository;

    @Transactional(readOnly = true)
    public AdminNotificationStaleTargetResponse getRecentTargets(Integer requestedLimit, Integer requestedOlderThanDays) {
        int limit = requestedLimit == null ? 5 : Math.max(1, Math.min(requestedLimit, 20));
        int olderThanDays = normalizeDays(requestedOlderThanDays);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);

        long staleRowCount = readRepository.countStaleRows(cutoff);
        long staleGroupCount = readRepository.countStaleGroups(cutoff);
        List<AdminNotificationStaleTargetResponse.Item> items = readRepository.findTargets(cutoff, limit)
                .stream()
                .map(row -> new AdminNotificationStaleTargetResponse.Item(
                        row.kind(),
                        row.title(),
                        row.deeplinkUrl(),
                        row.rowCount(),
                        row.userCount(),
                        row.oldestCreatedAt(),
                        row.newestCreatedAt()
                ))
                .toList();
        return new AdminNotificationStaleTargetResponse(
                olderThanDays,
                staleRowCount,
                staleGroupCount,
                items
        );
    }

    private int normalizeDays(Integer raw) {
        if (raw == null) {
            return 14;
        }
        if (raw < 1 || raw > 365) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return raw;
    }
}
