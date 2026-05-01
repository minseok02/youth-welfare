package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.ClusterAiResult;
import com.example.welfare.recommend.repository.ClusterAiResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JpaClusterAiScoreCache implements ClusterAiScoreCache {

    private final ClusterAiResultRepository clusterAiResultRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CachedClusterAiScore> findByClusterId(String clusterId) {
        return clusterAiResultRepository.findByClusterId(clusterId).stream()
                .collect(Collectors.toMap(
                        result -> result.getService().getId(),
                        result -> new CachedClusterAiScore(
                                result.getService().getId(),
                                result.getAiScore(),
                                result.getAiReason()
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Override
    @Transactional
    public void saveAll(String clusterId, List<ClusterAiScoreWrite> writes) {
        if (writes == null || writes.isEmpty()) {
            return;
        }

        Map<Long, ClusterAiResult> existingByServiceId = clusterAiResultRepository.findByClusterId(clusterId).stream()
                .collect(Collectors.toMap(result -> result.getService().getId(), result -> result));

        for (ClusterAiScoreWrite write : writes) {
            ClusterAiResult existing = existingByServiceId.get(write.service().getId());
            if (existing != null) {
                existing.update(write.aiScore(), write.aiReason());
                continue;
            }
            clusterAiResultRepository.save(ClusterAiResult.builder()
                    .clusterId(clusterId)
                    .service(write.service())
                    .aiScore(write.aiScore())
                    .aiReason(write.aiReason())
                    .build());
        }
    }
}
