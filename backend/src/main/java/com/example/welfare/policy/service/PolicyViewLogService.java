package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.ServiceViewLog;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceViewLogRepository;
import com.example.welfare.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PolicyViewLogService {

    private static final int DEDUP_WINDOW_HOURS = 24;

    private final ServiceViewLogRepository serviceViewLogRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional
    public boolean registerViewIfFirstInWindow(Long serviceId, Long userId, String clientFingerprint) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(DEDUP_WINDOW_HOURS);
        String userKey = resolveUserKey(userId);

        boolean duplicate = userKey != null
                ? serviceViewLogRepository.existsByServiceIdAndUserKeyAndViewedAtAfter(
                serviceId, userKey, cutoff
        )
                : serviceViewLogRepository.existsByServiceIdAndClientFingerprintAndViewedAtAfter(
                serviceId, clientFingerprint, cutoff
        );

        if (duplicate) {
            return false;
        }

        WelfareService serviceRef = entityManager.getReference(WelfareService.class, serviceId);
        serviceViewLogRepository.save(ServiceViewLog.builder()
                .service(serviceRef)
                .userKey(userKey)
                .clientFingerprint(clientFingerprint)
                .build());

        return true;
    }

    public String buildClientFingerprint(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return sha256((ip == null ? "" : ip) + "|" + (userAgent == null ? "" : userAgent));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String resolveUserKey(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findUserKeyById(userId).orElse(null);
    }
}
