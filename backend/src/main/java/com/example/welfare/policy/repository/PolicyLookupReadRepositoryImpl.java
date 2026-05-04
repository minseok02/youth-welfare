package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PolicyLookupReadRepositoryImpl implements PolicyLookupReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public Optional<WelfareService> findById(Long serviceId) {
        return welfareServiceRepository.findById(serviceId);
    }
}
