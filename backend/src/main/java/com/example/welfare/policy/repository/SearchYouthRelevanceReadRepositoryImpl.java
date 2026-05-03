package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SearchYouthRelevanceReadRepositoryImpl implements SearchYouthRelevanceReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public List<WelfareService> findBackfillTargetServices() {
        return welfareServiceRepository.findAll();
    }
}
