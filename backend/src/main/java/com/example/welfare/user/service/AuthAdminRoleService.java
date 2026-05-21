package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthAdminRoleService {

    @Value("${security.admin-emails:}")
    private String adminEmailsProperty;

    private Set<String> adminEmails = Set.of();
    private Set<String> adminEmailLookupHashes = Set.of();

    @PostConstruct
    void initAdminEmails() {
        adminEmails = Arrays.stream(adminEmailsProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        adminEmailLookupHashes = adminEmails.stream()
                .map(EmailLookupKeyGenerator::hash)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validatePublicSignupEmail(String email) {
        if (isReservedAdminEmail(email)) {
            throw new CustomException(ErrorCode.ADMIN_EMAIL_SIGNUP_FORBIDDEN);
        }
    }

    public List<String> resolveRoles(String email) {
        if (isReservedAdminEmail(email)) {
            return List.of("ROLE_USER", "ROLE_ADMIN");
        }
        return List.of("ROLE_USER");
    }

    public List<String> resolveRolesByEmailLookupHash(String emailLookupHash) {
        if (StringUtils.hasText(emailLookupHash) && adminEmailLookupHashes.contains(emailLookupHash)) {
            return List.of("ROLE_USER", "ROLE_ADMIN");
        }
        return List.of("ROLE_USER");
    }

    public boolean isReservedAdminEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return adminEmails.contains(EmailLookupKeyGenerator.normalize(email));
    }
}
