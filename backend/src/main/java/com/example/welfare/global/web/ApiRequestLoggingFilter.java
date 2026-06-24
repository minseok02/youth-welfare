package com.example.welfare.global.web;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.util.RedisKeyHash;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 80;

    private final ClientFingerprintService clientFingerprintService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return !(path.startsWith("/api/") || path.equals("/api"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedNanos = System.nanoTime();
        String requestId = resolveRequestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            failure = exception;
            throw exception;
        } finally {
            logRequest(request, response, requestId, elapsedMs(startedNanos), failure);
        }
    }

    private void logRequest(HttpServletRequest request,
                            HttpServletResponse response,
                            String requestId,
                            long elapsedMs,
                            Exception failure) {
        int status = response.getStatus();
        String errorCode = ObservabilityAttributes.getErrorCode(request);
        String errorType = failure != null ? failure.getClass().getSimpleName() : null;

        log.info("[ApiRequest] method={} path={} status={} durationMs={} requestId={} userKeyHash={} clientFingerprint={} errorCode={} errorType={} refererPresent={}",
                request.getMethod(),
                requestPath(request),
                status,
                elapsedMs,
                requestId,
                currentUserKeyHash(),
                clientFingerprintService.build(request),
                errorCode,
                errorType,
                StringUtils.hasText(request.getHeader(HttpHeaders.REFERER)));
    }

    private String currentUserKeyHash() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            return null;
        }
        if (authenticatedUser.hasUserKey()) {
            return RedisKeyHash.sha256Hex(authenticatedUser.userKey());
        }
        return authenticatedUser.hasUserId() ? RedisKeyHash.sha256Hex(String.valueOf(authenticatedUser.userId())) : null;
    }

    private long elapsedMs(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String provided = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(provided)) {
            String sanitized = provided.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
            return sanitized.length() <= MAX_REQUEST_ID_LENGTH
                    ? sanitized
                    : sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
        }
        return UUID.randomUUID().toString();
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
