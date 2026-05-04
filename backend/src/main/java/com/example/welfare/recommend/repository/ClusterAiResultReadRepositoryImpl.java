package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ClusterAiResultReadRepositoryImpl implements ClusterAiResultReadRepository {

    private final ClusterAiResultRepository clusterAiResultRepository;

    @Override
    public List<ClusterAiResult> findByClusterId(String clusterId) {
        return clusterAiResultRepository.findByClusterId(clusterId);
    }
}
