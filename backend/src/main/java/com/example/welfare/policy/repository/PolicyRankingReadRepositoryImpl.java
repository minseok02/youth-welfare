package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PolicyRankingReadRepositoryImpl implements PolicyRankingReadRepository {

    private static final List<WelfareService.ServiceStatus> RANKABLE_STATUSES = List.of(
            WelfareService.ServiceStatus.ACTIVE,
            WelfareService.ServiceStatus.UPCOMING
    );

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public List<WelfareService> findRankableServices() {
        return welfareServiceRepository.findByStatusIn(RANKABLE_STATUSES);
    }
}
