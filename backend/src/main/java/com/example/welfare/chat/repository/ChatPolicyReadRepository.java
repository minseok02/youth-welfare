package com.example.welfare.chat.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface ChatPolicyReadRepository {

    List<WelfareService> findCandidates(ChatPolicyReadCondition condition);
}
