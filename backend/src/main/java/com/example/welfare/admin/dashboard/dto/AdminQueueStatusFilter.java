package com.example.welfare.admin.dashboard.dto;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;

public enum AdminQueueStatusFilter {
    OPEN,
    REVIEWED,
    ALL;

    public static AdminQueueStatusFilter fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPEN;
        }
        try {
            return AdminQueueStatusFilter.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
