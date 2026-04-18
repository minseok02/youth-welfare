package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAttributeRepository extends JpaRepository<UserAttribute, Long> {

    List<UserAttribute> findByUserId(Long userId);

    List<UserAttribute> findByUserIdAndAttrType(Long userId, String attrType);

    void deleteByUserId(Long userId);

    void deleteByUserIdAndAttrType(Long userId, String attrType);
}
