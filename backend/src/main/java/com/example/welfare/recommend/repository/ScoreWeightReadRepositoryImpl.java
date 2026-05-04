package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ScoreWeight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ScoreWeightReadRepositoryImpl implements ScoreWeightReadRepository {

    private final ScoreWeightRepository scoreWeightRepository;

    @Override
    public List<ScoreWeight> findConfiguredActiveWeights() {
        return scoreWeightRepository.findByIsActiveTrueOrderByMinLogCountAsc();
    }
}
