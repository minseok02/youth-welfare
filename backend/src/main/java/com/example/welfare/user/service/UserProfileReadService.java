package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.repository.UserProfileReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserProfileReadService {

    private final UserProfileReadRepository userProfileReadRepository;
    private final AesEncryptUtil aesEncryptUtil;
    private final UserKeyLookupService userKeyLookupService;
    private final AuthIdentityReadService authIdentityReadService;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        String userKey = resolveActiveUserKey(userId);
        var aggregate = userProfileReadRepository.findProfileAggregateByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return ProfileResponse.of(
                userId,
                decryptNullable(aggregate.pii().emailEnc()),
                decryptNullable(aggregate.pii().nameEnc()),
                parseBirthDate(aggregate.pii().birthDateEnc()),
                aggregate.profile(),
                aggregate.attributes(),
                aggregate.priorities()
        );
    }

    private String resolveActiveUserKey(Long userId) {
        String userKey = userKeyLookupService.findRequired(userId);
        return authIdentityReadService.requireActiveUserKey(userKey);
    }

    private String decryptNullable(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        return aesEncryptUtil.decrypt(encryptedValue);
    }

    private LocalDate parseBirthDate(String encryptedBirthDate) {
        String birthDate = decryptNullable(encryptedBirthDate);
        if (!StringUtils.hasText(birthDate)) {
            return null;
        }
        return LocalDate.parse(birthDate);
    }
}
