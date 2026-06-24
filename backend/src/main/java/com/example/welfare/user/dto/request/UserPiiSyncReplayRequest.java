package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UserPiiSyncReplayRequest(
        @Size(max = 32, message = "userKey는 32자 이하여야 합니다.")
        String userKey,
        @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "limit는 1000 이하여야 합니다.")
        Integer limit
) {
}
