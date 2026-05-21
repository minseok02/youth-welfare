package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserPiiReadModel;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
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

        String email = StringUtils.hasText(user.getEmail())
                ? user.getEmail()
                : decryptNullable(stored != null ? stored.emailEnc() : null);
        String name = StringUtils.hasText(user.getName())
                ? user.getName()
                : decryptNullable(stored != null ? stored.nameEnc() : null);
        LocalDate birthDate = user.getBirthDate() != null
                ? user.getBirthDate()
                : decryptBirthDate(stored != null ? stored.birthDateEnc() : null);

        return new UserPlainPii(email, name, birthDate);
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
}
