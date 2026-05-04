package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.repository.ServiceTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CollectItemTagCommandRepositoryImpl implements CollectItemTagCommandRepository {

    private final ServiceTagRepository serviceTagRepository;

    @Override
    public void replaceAll(Long serviceId, List<ServiceTag> tags) {
        serviceTagRepository.deleteByServiceId(serviceId);
        serviceTagRepository.flush();
        if (!tags.isEmpty()) {
            serviceTagRepository.saveAll(tags);
        }
    }
}
