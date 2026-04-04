package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPriority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPriorityRepository extends JpaRepository<UserPriority, Long> {

    List<UserPriority> findByUserIdOrderByPriorityRank(Long userId);

    void deleteByUserId(Long userId);
}
