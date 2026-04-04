package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceTagRepository extends JpaRepository<ServiceTag, Long> {

    List<ServiceTag> findByServiceId(Long serviceId);

    List<ServiceTag> findByTagTypeAndTagValue(ServiceTag.TagType tagType, String tagValue);

    // UPSERT — UNIQUE KEY uq_st(service_id, tag_type, tag_value) 기반
    // 중복 삽입 시 rule_base_score 이중합산 방지
    @Modifying
    @Query(value = """
            INSERT INTO service_tags (service_id, tag_type, tag_value)
            VALUES (:serviceId, :tagType, :tagValue)
            ON DUPLICATE KEY UPDATE tag_value = tag_value
            """, nativeQuery = true)
    void upsert(@Param("serviceId") Long serviceId,
                @Param("tagType") String tagType,
                @Param("tagValue") String tagValue);
}
