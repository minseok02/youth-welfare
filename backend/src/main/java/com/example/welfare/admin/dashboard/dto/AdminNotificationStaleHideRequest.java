package com.example.welfare.admin.dashboard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminNotificationStaleHideRequest(
        @NotBlank(message = "kind는 필수입니다.")
        @Size(max = 64, message = "kind는 64자 이하여야 합니다.")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "kind 형식이 올바르지 않습니다.")
        String kind,
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 200, message = "title은 200자 이하여야 합니다.")
        String title,
        @NotBlank(message = "deeplinkUrl은 필수입니다.")
        @Size(max = 500, message = "deeplinkUrl은 500자 이하여야 합니다.")
        @Pattern(regexp = "^/(?!/)[^\\r\\n]*$", message = "deeplinkUrl 형식이 올바르지 않습니다.")
        String deeplinkUrl,
        @Min(value = 1, message = "olderThanDays는 1 이상이어야 합니다.")
        @Max(value = 365, message = "olderThanDays는 365 이하여야 합니다.")
        Integer olderThanDays
) {
}
