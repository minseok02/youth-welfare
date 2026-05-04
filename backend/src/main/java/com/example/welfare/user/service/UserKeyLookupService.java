package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.repository.UserKeyReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserKeyLookupService {

    private final UserKeyReadRepository userKeyReadRepository;

    public String findNullable(Long userId) {
        if (userId == null) {
            return null;
        }
        return userKeyReadRepository.findUserKeyById(userId).orElse(null);
    }

    public String findRequired(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return userKeyReadRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
