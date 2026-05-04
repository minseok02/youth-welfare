package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;

import java.time.LocalDateTime;

public interface ClusterAiResultCommandRepository {

    ClusterAiResult save(ClusterAiResult clusterAiResult);

    void deleteExpiredBefore(LocalDateTime before);
}
