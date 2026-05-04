package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CollectItemCommandRepositoryImpl implements CollectItemCommandRepository {

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public WelfareService saveAndFlush(WelfareService service) {
        return welfareServiceRepository.saveAndFlush(service);
    }
}
