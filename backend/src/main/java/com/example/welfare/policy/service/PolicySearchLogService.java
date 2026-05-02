package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.SearchLog;
import com.example.welfare.policy.repository.SearchLogRepository;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicySearchLogService {

    private final SearchLogRepository searchLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(PolicySearchLogCommand command) {
        if (command == null || command.keyword() == null || command.keyword().isBlank()) {
            return;
        }

        try {
            searchLogRepository.save(SearchLog.builder()
                    .userKey(resolveUserKey(command.userId()))
                    .clientFingerprint(normalizeClientFingerprint(command.clientFingerprint()))
                    .keyword(command.keyword().trim())
                    .resultCount(command.resultCount())
                    .statusFilter(normalizeUpper(command.status()))
                    .includeClosed(Boolean.TRUE.equals(command.includeClosed()))
                    .category(normalizeUpper(command.category()))
                    .sourceType(normalizeUpper(command.sourceType()))
                    .onlineApply(command.onlineApply())
                    .sido(normalizeNullable(command.sido()))
                    .sgg(normalizeNullable(command.sgg()))
                    .sortKey(normalizeUpper(command.sort()))
                    .pageNumber(Math.max(0, command.page()))
                    .pageSize(Math.max(1, command.size()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("[PolicySearchLogService] 검색 로그 저장 실패 keyword={} resultCount={} page={} size={}",
                    command.keyword(),
                    command.resultCount(),
                    command.page(),
                    command.size(),
                    e);
        }
    }

    private String resolveUserKey(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findUserKeyById(userId).orElse(null);
    }

    private String normalizeClientFingerprint(String clientFingerprint) {
        String normalized = normalizeNullable(clientFingerprint);
        return normalized != null ? normalized : "anonymous";
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeNullable(value);
        return normalized != null ? normalized.toUpperCase() : null;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
