package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PolicyRankingReadRepositoryImpl implements PolicyRankingReadRepository {

    private static final List<WelfareService.ServiceStatus> RANKABLE_STATUSES = List.of(
            WelfareService.ServiceStatus.ACTIVE,
            WelfareService.ServiceStatus.UPCOMING
    );

    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceViewLogRepository serviceViewLogRepository;

    @Override
    public List<WelfareService> findRankableServices() {
        return welfareServiceRepository.findByStatusIn(RANKABLE_STATUSES);
    }

    @Override
    public List<ServiceUniqueViewCount> findUniqueViewCountsSince(Collection<Long> serviceIds, LocalDateTime cutoff) {
        return serviceViewLogRepository.findUniqueViewCountsSince(serviceIds, cutoff).stream()
                .<ServiceUniqueViewCount>map(row -> new RankingUniqueViewCount(row.getServiceId(), row.getUniqueViewCount()))
                .toList();
    }

    private record RankingUniqueViewCount(Long getServiceId, Long getUniqueViewCount)
            implements ServiceUniqueViewCount {
    }
}
