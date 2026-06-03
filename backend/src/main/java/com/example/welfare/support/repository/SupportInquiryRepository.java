package com.example.welfare.support.repository;

import com.example.welfare.support.entity.SupportInquiry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {

    long countByStatus(SupportInquiry.Status status);

    List<SupportInquiry> findByStatusOrderByCreatedAtDesc(SupportInquiry.Status status, Pageable pageable);
}
