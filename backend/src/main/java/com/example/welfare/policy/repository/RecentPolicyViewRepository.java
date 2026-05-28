package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.RecentPolicyView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface RecentPolicyViewRepository extends JpaRepository<RecentPolicyView, Long> {

    @Query("""
            SELECT rpv
            FROM RecentPolicyView rpv
            JOIN FETCH rpv.service
            WHERE rpv.userKey = :userKey
            ORDER BY rpv.lastViewedAt DESC, rpv.id DESC
            """)
    List<RecentPolicyView> findRecentViewsByUserKey(@Param("userKey") String userKey, Pageable pageable);

    @Modifying
    @Transactional
    void deleteByUserKeyIn(Collection<String> userKeys);
}
