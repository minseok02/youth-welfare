package com.example.welfare.chat.repository;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface ChatPolicyReadRepository {

    List<WelfareService> findCandidates(ChatPolicyReadCondition condition);

    com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace traceCandidates(ChatPolicyReadCondition condition);

    com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace traceCandidates(
            ChatPolicyReadCondition condition,
            ChatRetrievalProperties tuning
    );
}
