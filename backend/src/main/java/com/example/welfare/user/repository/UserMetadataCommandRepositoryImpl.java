package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserMetadataCommandRepositoryImpl implements UserMetadataCommandRepository {

    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;

    @Override
    public void replaceAttributes(Long userId, String userKey, String attrType, List<String> values) {
        userAttributeRepository.deleteByUserKeyAndAttrType(userKey, attrType);
        values.forEach(value ->
                userAttributeRepository.save(UserAttribute.builder()
                        .userId(userId)
                        .userKey(userKey)
                        .attrType(attrType)
                        .attrValue(value)
                        .build())
        );
    }

    @Override
    public void replacePriorities(String userKey, List<UserPriority> priorities) {
        userPriorityRepository.deleteByUserKey(userKey);
        priorities.forEach(userPriorityRepository::save);
    }

    @Override
    public void deleteAllByUserKey(String userKey) {
        userAttributeRepository.deleteByUserKey(userKey);
        userPriorityRepository.deleteByUserKey(userKey);
    }

    @Override
    public boolean hasAttributeValues(String userKey, String attrType) {
        return !userAttributeRepository.findByUserKeyAndAttrType(userKey, attrType).isEmpty();
    }

    @Override
    public boolean hasPriorities(String userKey) {
        return userPriorityRepository.existsByUserKey(userKey);
    }
}
