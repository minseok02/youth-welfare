package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceTagRepository extends JpaRepository<ServiceTag, Long> {

    List<ServiceTag> findByServiceId(Long serviceId);

    @Modifying
    void deleteByServiceId(Long serviceId);

    // 후보 전체 태그 일괄 조회 — N+1 방지 (JOIN FETCH로 service 즉시 로딩)
    @Query("SELECT t FROM ServiceTag t JOIN FETCH t.service WHERE t.service.id IN :serviceIds")
    List<ServiceTag> findByServiceIdIn(@Param("serviceIds") List<Long> serviceIds);

    List<ServiceTag> findByTagTypeAndTagValue(ServiceTag.TagType tagType, String tagValue);

    List<ServiceTag> findByServiceIdInAndTagType(List<Long> serviceIds, ServiceTag.TagType tagType);

}
