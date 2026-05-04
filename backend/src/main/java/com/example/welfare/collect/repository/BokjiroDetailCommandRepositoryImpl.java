package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BokjiroDetailCommandRepositoryImpl implements BokjiroDetailCommandRepository {

    private final WelfareServiceDetailRepository welfareServiceDetailRepository;

    @Override
    public WelfareServiceDetail save(WelfareServiceDetail detail) {
        return welfareServiceDetailRepository.save(detail);
    }
}
