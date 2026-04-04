package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ScoreWeight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScoreWeightRepository extends JpaRepository<ScoreWeight, Long> {

    Optional<ScoreWeight> findByWeightKey(String weightKey);

    List<ScoreWeight> findByIsActiveTrueOrderByMinLogCountAsc();
}
