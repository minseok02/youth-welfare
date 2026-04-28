package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPriorityRepository extends JpaRepository<UserPriority, Long> {

    List<UserPriority> findByUserIdOrderByPriorityRank(Long userId);

    @Query(value = """
            select up.priority_rank as priorityRank,
                   po.code as code,
                   up.weight as weight
            from user_priorities up
            join priority_options po on po.id = up.priority_option_id
            where up.user_key = ?1
            order by up.priority_rank
            """, nativeQuery = true)
    List<UserPriorityReadModel> findReadModelsByUserKey(String userKey);

    @Query(value = """
            select count(*)
            from user_priorities
            where user_key is null or user_key = ''
            """, nativeQuery = true)
    int countMissingUserKeys();

    @Modifying
    @Query(value = """
            update user_priorities up
            join users u on u.id = up.user_id
            set up.user_key = u.user_key
            where up.user_key is null or up.user_key = ''
            """, nativeQuery = true)
    int backfillMissingUserKeys();

    // @Modifying으로 즉시 DELETE를 DB에 반영 — 같은 트랜잭션에서 INSERT 전 flush 보장
    @Modifying
    @Query("DELETE FROM UserPriority up WHERE up.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
