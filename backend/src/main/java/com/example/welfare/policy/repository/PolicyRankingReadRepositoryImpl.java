package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    public List<RankableServiceSnapshot> findRankableSnapshots() {
        return welfareServiceRepository.findRankableSnapshotsByStatusIn(RANKABLE_STATUSES);
    }

    @Override
    public List<ServiceUniqueViewCount> findUniqueViewCountsSince(java.time.LocalDateTime cutoff) {
        return serviceViewLogRepository.findUniqueViewCountsSince(cutoff).stream()
                .<ServiceUniqueViewCount>map(row -> new RankingUniqueViewCount(row.getServiceId(), row.getUniqueViewCount()))
                .toList();
    }

    @Override
    public List<WelfareService> findServicesByIds(Collection<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = serviceIds.stream().toList();
        Map<Long, WelfareService> byId = new LinkedHashMap<>();
        for (WelfareService service : welfareServiceRepository.findAllById(orderedIds)) {
            byId.put(service.getId(), service);
        }
        return orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private record RankingUniqueViewCount(Long getServiceId, Long getUniqueViewCount)
            implements ServiceUniqueViewCount {
    }
}
