package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPriority;

import java.util.List;

public interface UserMetadataCommandRepository {

    void replaceAttributes(Long userId, String userKey, String attrType, List<String> values);

    void replacePriorities(String userKey, List<UserPriority> priorities);

    void deleteAllByUserKey(String userKey);

    boolean hasAttributeValues(String userKey, String attrType);

    boolean hasPriorities(String userKey);
}
