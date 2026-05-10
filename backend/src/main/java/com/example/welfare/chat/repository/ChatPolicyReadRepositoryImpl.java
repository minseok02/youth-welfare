package com.example.welfare.chat.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatPolicyReadRepositoryImpl implements ChatPolicyReadRepository {

    private static final List<WelfareService.ServiceStatus> SEARCHABLE_STATUSES = List.of(
            WelfareService.ServiceStatus.ACTIVE,
            WelfareService.ServiceStatus.UPCOMING
    );

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public List<WelfareService> findCandidates(ChatPolicyReadCondition condition) {
        List<WelfareService> candidates = List.of();
        if (StringUtils.hasText(condition.keyword())) {
            candidates = welfareServiceRepository.searchChatCandidates(condition.keyword(), condition.limit());
        }
        if (!candidates.isEmpty()) {
            return candidates;
        }
        return welfareServiceRepository.findBySearchYouthRelevantTrueAndStatusInOrderByApiViewCountDescViewCountDescCreatedAtDesc(
                SEARCHABLE_STATUSES,
                PageRequest.of(0, condition.limit())
        );
    }
}
