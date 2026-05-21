package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserPiiReadModel;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.util.UserEmailShadowValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserPlainPiiReadService {

    private final UserPiiReadWriteRepository userPiiReadWriteRepository;
    private final AesEncryptUtil aesEncryptUtil;

    public UserPlainPii resolveCurrent(User user, String userKey) {
        UserPiiReadModel stored = userPiiReadWriteRepository.findByUserKey(userKey)
                .orElse(null);

        String email = firstText(
                decryptNullable(stored != null ? stored.emailEnc() : null),
                fallbackPlainEmail(user.getEmail())
        );
        String name = firstText(
                decryptNullable(stored != null ? stored.nameEnc() : null),
                user.getName()
        );
        LocalDate birthDate = firstDate(
                decryptBirthDate(stored != null ? stored.birthDateEnc() : null),
                user.getBirthDate()
        );

        return new UserPlainPii(email, name, birthDate);
    }

    private String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        return StringUtils.hasText(fallback) ? fallback : null;
    }

    private LocalDate firstDate(LocalDate preferred, LocalDate fallback) {
        return preferred != null ? preferred : fallback;
    }

    private String decryptNullable(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        return aesEncryptUtil.decrypt(encryptedValue);
    }

    private LocalDate decryptBirthDate(String encryptedValue) {
        String decrypted = decryptNullable(encryptedValue);
        if (!StringUtils.hasText(decrypted)) {
            return null;
        }
        return LocalDate.parse(decrypted);
    }

    private String fallbackPlainEmail(String storedEmail) {
        if (!StringUtils.hasText(storedEmail) || UserEmailShadowValue.isShadowValue(storedEmail)) {
            return null;
        }
        return storedEmail;
    }
}
