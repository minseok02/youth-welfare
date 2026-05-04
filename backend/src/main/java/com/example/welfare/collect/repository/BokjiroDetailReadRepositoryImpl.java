package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BokjiroDetailReadRepositoryImpl implements BokjiroDetailReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceDetailRepository welfareServiceDetailRepository;

    @Override
    public List<WelfareService> findTargetsBySourceType(WelfareService.SourceType sourceType) {
        return welfareServiceRepository.findBySourceType(sourceType);
    }

    @Override
    public boolean existsDetailByServiceId(Long serviceId) {
        return welfareServiceDetailRepository.existsByServiceId(serviceId);
    }

    @Override
    public Optional<WelfareServiceDetail> findDetailByServiceId(Long serviceId) {
        return welfareServiceDetailRepository.findByServiceId(serviceId);
    }
}
