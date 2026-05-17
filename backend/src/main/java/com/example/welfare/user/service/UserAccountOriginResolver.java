package com.example.welfare.user.service;

import com.example.welfare.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserAccountOriginResolver {

    public User.AccountOrigin resolve(String email) {
        String normalizedDomain = normalizeDomain(email);
        if ("example.com".equals(normalizedDomain)) {
            return User.AccountOrigin.EXAMPLE_SMOKE;
        }
        if ("smoke.local".equals(normalizedDomain)
                || "localhost".equals(normalizedDomain)
                || normalizedDomain.endsWith(".local")
                || normalizedDomain.endsWith(".test")
                || normalizedDomain.endsWith(".invalid")) {
            return User.AccountOrigin.BOUNDED_LOCAL;
        }
        if ("cohortseed.app".equals(normalizedDomain)) {
            return User.AccountOrigin.LOCAL_REAL_NON_EXAMPLE_SEED;
        }
        return User.AccountOrigin.REAL_USER;
    }

    private String normalizeDomain(String email) {
        if (email == null) {
            return "";
        }
        String trimmed = email.trim().toLowerCase();
        int atIndex = trimmed.lastIndexOf('@');
        if (atIndex < 0 || atIndex == trimmed.length() - 1) {
            return "";
        }
        return trimmed.substring(atIndex + 1);
    }
}
