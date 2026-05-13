package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserAttributeRepository extends JpaRepository<UserAttribute, Long> {

    List<UserAttribute> findByUserKey(String userKey);

    List<UserAttribute> findByUserKeyAndAttrType(String userKey, String attrType);

    @Query(value = """
            select attr_type as attrType, attr_value as attrValue
            from user_attributes ua
            where ua.user_key = ?1
            order by ua.id
            """, nativeQuery = true)
    List<UserAttributeReadModel> findReadModelsByUserKey(String userKey);

    @Query(value = """
            select count(*)
            from user_attributes
            where user_key is null or user_key = ''
            """, nativeQuery = true)
    int countMissingUserKeys();

    @Modifying
    @Query(value = """
            update user_attributes ua
            set user_key = u.user_key
            from users u
            where u.id = ua.user_id
              and (ua.user_key is null or ua.user_key = '')
            """, nativeQuery = true)
    int backfillMissingUserKeys();

    @Modifying
    @Query("""
            DELETE FROM UserAttribute ua
            WHERE ua.userKey = :userKey
            """)
    void deleteByUserKey(@Param("userKey") String userKey);

    @Modifying
    @Query("""
            DELETE FROM UserAttribute ua
            WHERE ua.userKey = :userKey
              AND ua.attrType = :attrType
            """)
    void deleteByUserKeyAndAttrType(@Param("userKey") String userKey, @Param("attrType") String attrType);
}
