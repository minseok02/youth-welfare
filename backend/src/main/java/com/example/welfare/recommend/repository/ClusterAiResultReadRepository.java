package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;

import java.util.List;

public interface ClusterAiResultReadRepository {

    List<ClusterAiResult> findByClusterId(String clusterId);
}
