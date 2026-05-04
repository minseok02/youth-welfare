package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.SearchLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PolicySearchLogCommandRepositoryImpl implements PolicySearchLogCommandRepository {

    private final SearchLogRepository searchLogRepository;

    @Override
    public SearchLog save(SearchLog searchLog) {
        return searchLogRepository.save(searchLog);
    }
}
