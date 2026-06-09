package com.example.welfare.policy.dto;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PolicyRetrievalEvaluationCompareRequest(
        @Min(value = 1, message = "minResultCount는 1 이상이어야 합니다.")
        @Max(value = 200, message = "minResultCount는 200 이하여야 합니다.")
        Integer minResultCount,
        @Min(value = 1, message = "semanticBlendLimit는 1 이상이어야 합니다.")
        @Max(value = 500, message = "semanticBlendLimit는 500 이하여야 합니다.")
        Integer semanticBlendLimit,
        @Min(value = 1, message = "semanticOnlyLimit는 1 이상이어야 합니다.")
        @Max(value = 500, message = "semanticOnlyLimit는 500 이하여야 합니다.")
        Integer semanticOnlyLimit,
        @Min(value = 1, message = "maxPreferredTermsInSearchKeyword는 1 이상이어야 합니다.")
        @Max(value = 50, message = "maxPreferredTermsInSearchKeyword는 50 이하여야 합니다.")
        Integer maxPreferredTermsInSearchKeyword
) {

    public ChatRetrievalProperties mergeWith(ChatRetrievalProperties base) {
        return new ChatRetrievalProperties(
                minResultCount != null ? minResultCount : base.minResultCount(),
                semanticBlendLimit != null ? semanticBlendLimit : base.semanticBlendLimit(),
                semanticOnlyLimit != null ? semanticOnlyLimit : base.semanticOnlyLimit(),
                maxPreferredTermsInSearchKeyword != null
                        ? maxPreferredTermsInSearchKeyword
                        : base.maxPreferredTermsInSearchKeyword()
        );
    }
}
