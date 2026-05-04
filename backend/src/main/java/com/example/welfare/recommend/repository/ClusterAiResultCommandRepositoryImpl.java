package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ClusterAiResultCommandRepositoryImpl implements ClusterAiResultCommandRepository {

    private final ClusterAiResultRepository clusterAiResultRepository;

    @Override
    public ClusterAiResult save(ClusterAiResult clusterAiResult) {
        return clusterAiResultRepository.save(clusterAiResult);
    }

    @Override
    public void deleteExpiredBefore(LocalDateTime before) {
        clusterAiResultRepository.deleteExpiredBefore(before);
    }
}
