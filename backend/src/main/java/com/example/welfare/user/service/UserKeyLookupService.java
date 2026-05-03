package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserKeyLookupService {

    private final UserRepository userRepository;

    public String findNullable(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findUserKeyById(userId).orElse(null);
    }

    public String findRequired(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
