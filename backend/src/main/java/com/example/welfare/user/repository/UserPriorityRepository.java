package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPriorityRepository extends JpaRepository<UserPriority, Long> {

    List<UserPriority> findByUserIdOrderByPriorityRank(Long userId);

    // @Modifying으로 즉시 DELETE를 DB에 반영 — 같은 트랜잭션에서 INSERT 전 flush 보장
    @Modifying
    @Query("DELETE FROM UserPriority up WHERE up.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
