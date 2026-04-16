package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceTagRepository extends JpaRepository<ServiceTag, Long> {

    List<ServiceTag> findByServiceId(Long serviceId);

    // 후보 전체 태그 일괄 조회 — N+1 방지 (JOIN FETCH로 service 즉시 로딩)
    @Query("SELECT t FROM ServiceTag t JOIN FETCH t.service WHERE t.service.id IN :serviceIds")
    List<ServiceTag> findByServiceIdIn(@Param("serviceIds") List<Long> serviceIds);

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
