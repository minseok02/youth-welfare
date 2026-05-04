package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserAccountReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserReadService {

    private final UserAccountReadRepository userAccountReadRepository;
    private final UserKeyLookupService userKeyLookupService;

    @Transactional(readOnly = true)
    public ActiveUserContext getActiveUserContext(Long userId) {
        User user = userAccountReadRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        String userKey = user.getUserKey() != null ? user.getUserKey() : userKeyLookupService.findRequired(userId);
        return new ActiveUserContext(user, userKey);
    }

    @Transactional(readOnly = true)
    public User getActiveUserByUserKey(String userKey) {
        return findOptionalActiveUserByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<User> findOptionalActiveUserByUserKey(String userKey) {
        return userAccountReadRepository.findByUserKey(userKey)
                .filter(User::isActive);
    }

    @Transactional(readOnly = true)
    public Long requireExistingUserIdByUserKey(String userKey) {
        return userKeyLookupService.findRequiredUserId(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public record ActiveUserContext(
            User user,
            String userKey
    ) {
    }
}
