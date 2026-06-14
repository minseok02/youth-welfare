package com.example.welfare.support.repository;

import com.example.welfare.support.entity.SupportInquiry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {

    long countByStatus(SupportInquiry.Status status);

    long countByStatusAndCreatedAtAfter(SupportInquiry.Status status, LocalDateTime createdAt);

    List<SupportInquiry> findByStatusOrderByCreatedAtDesc(SupportInquiry.Status status, Pageable pageable);

    List<SupportInquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SupportInquiry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
