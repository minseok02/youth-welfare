package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserReadService {

    private final UserKeyLookupService userKeyLookupService;

    @Transactional(readOnly = true)
    public Long requireExistingUserIdByUserKey(String userKey) {
        return userKeyLookupService.findRequiredUserId(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
