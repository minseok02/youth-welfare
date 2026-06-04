package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyLinkReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyLinkReviewRecordRepository extends JpaRepository<PolicyLinkReviewRecord, Long> {

    Optional<PolicyLinkReviewRecord> findByPolicyId(Long policyId);
}
