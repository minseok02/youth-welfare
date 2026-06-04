package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyDuplicateReviewRecord;
import com.example.welfare.policy.entity.WelfareService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyDuplicateReviewRecordRepository extends JpaRepository<PolicyDuplicateReviewRecord, Long> {

    Optional<PolicyDuplicateReviewRecord> findBySourceTypeAndTitleAndHostOrgKey(
            WelfareService.SourceType sourceType,
            String title,
            String hostOrgKey
    );
}
