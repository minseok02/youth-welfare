package com.example.welfare.chat.repository;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.PolicyExplorationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatPolicyReadRepositoryImpl implements ChatPolicyReadRepository {
    private final PolicyExplorationService policyExplorationService;

    @Override
    public List<WelfareService> findCandidates(ChatPolicyReadCondition condition) {
        return policyExplorationService.findChatCandidates(condition);
    }

    @Override
    public PolicyExplorationService.ChatExplorationTrace traceCandidates(ChatPolicyReadCondition condition) {
        return policyExplorationService.traceChatCandidates(condition);
    }

    @Override
    public PolicyExplorationService.ChatExplorationTrace traceCandidates(ChatPolicyReadCondition condition,
                                                                         ChatRetrievalProperties tuning) {
        return policyExplorationService.traceChatCandidates(condition, tuning);
    }
}
