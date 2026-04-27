package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserAttributeRepository extends JpaRepository<UserAttribute, Long> {

    List<UserAttribute> findByUserId(Long userId);

    List<UserAttribute> findByUserIdAndAttrType(Long userId, String attrType);

    @Query(value = """
            select attr_type as attrType, attr_value as attrValue
            from user_attributes ua
            join users u on u.id = ua.user_id
            where u.user_key = ?1
            order by ua.id
            """, nativeQuery = true)
    List<UserAttributeReadModel> findReadModelsByUserKey(String userKey);

    void deleteByUserId(Long userId);

    void deleteByUserIdAndAttrType(Long userId, String attrType);
}
