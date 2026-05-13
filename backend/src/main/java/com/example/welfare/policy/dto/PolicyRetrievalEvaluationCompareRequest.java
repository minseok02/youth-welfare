package com.example.welfare.policy.dto;

import com.example.welfare.chat.config.ChatRetrievalProperties;

public record PolicyRetrievalEvaluationCompareRequest(
        Integer minResultCount,
        Integer semanticBlendLimit,
        Integer semanticOnlyLimit,
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
