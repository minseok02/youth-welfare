package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StatusUpdateReadRepositoryImpl implements StatusUpdateReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public List<WelfareService> findActiveServices() {
        return welfareServiceRepository.findByStatus(WelfareService.ServiceStatus.ACTIVE);
    }

    @Override
    public List<WelfareService> findUpcomingServices() {
        return welfareServiceRepository.findByStatus(WelfareService.ServiceStatus.UPCOMING);
    }
}
