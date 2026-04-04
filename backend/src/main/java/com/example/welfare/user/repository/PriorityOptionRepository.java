package com.example.welfare.user.repository;

import com.example.welfare.user.entity.PriorityOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriorityOptionRepository extends JpaRepository<PriorityOption, Byte> {

    Optional<PriorityOption> findByCode(String code);
}
