package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ScoreWeight;

import java.util.List;

public interface ScoreWeightReadRepository {

    List<ScoreWeight> findConfiguredActiveWeights();
}
