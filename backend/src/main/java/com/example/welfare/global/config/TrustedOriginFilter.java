package com.example.welfare.global.config;

import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class TrustedOriginFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/notifications/unsubscribe"
    );

    private final String allowedOriginsCsv;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!requiresTrustedOrigin(request) || isTrusted(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeSecurityError(response);
    }

    private boolean requiresTrustedOrigin(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && PROTECTED_PATHS.contains(requestPath(request));
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isTrusted(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (StringUtils.hasText(origin)) {
            return allowedOrigins().contains(normalizeOrigin(origin));
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        if (StringUtils.hasText(referer)) {
            String refererOrigin = normalizeOrigin(referer);
            return StringUtils.hasText(refererOrigin) && allowedOrigins().contains(refererOrigin);
        }

        return true;
    }

    private Set<String> allowedOrigins() {
        return Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::normalizeOrigin)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private String normalizeOrigin(String rawValue) {
        try {
            URI uri = URI.create(rawValue);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return "";
            }
            int port = uri.getPort();
            return port >= 0
                    ? "%s://%s:%d".formatted(uri.getScheme().toLowerCase(), uri.getHost().toLowerCase(), port)
                    : "%s://%s".formatted(uri.getScheme().toLowerCase(), uri.getHost().toLowerCase());
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private void writeSecurityError(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }
}
