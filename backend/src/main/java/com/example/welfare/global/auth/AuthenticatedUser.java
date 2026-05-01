package com.example.welfare.global.auth;

import org.springframework.util.StringUtils;

public record AuthenticatedUser(Long userId, String userKey) {

    public boolean hasUserId() {
        return userId != null;
    }

    public boolean hasUserKey() {
        return StringUtils.hasText(userKey);
    }
}
