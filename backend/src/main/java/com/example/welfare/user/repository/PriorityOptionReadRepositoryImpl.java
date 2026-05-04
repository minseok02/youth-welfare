package com.example.welfare.user.repository;

import com.example.welfare.user.entity.PriorityOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PriorityOptionReadRepositoryImpl implements PriorityOptionReadRepository {

    private final PriorityOptionRepository priorityOptionRepository;

    @Override
    public Optional<PriorityOption> findByCode(String code) {
        return priorityOptionRepository.findByCode(code);
    }
}
