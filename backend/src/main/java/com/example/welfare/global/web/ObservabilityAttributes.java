package com.example.welfare.global.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class ObservabilityAttributes {

    public static final String ERROR_CODE_ATTRIBUTE = ObservabilityAttributes.class.getName() + ".errorCode";

    private ObservabilityAttributes() {
    }

    public static void setErrorCode(HttpServletRequest request, String errorCode) {
        if (request != null && StringUtils.hasText(errorCode)) {
            request.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode);
        }
    }

    public static String getErrorCode(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        return value instanceof String errorCode && StringUtils.hasText(errorCode) ? errorCode : null;
    }
}
