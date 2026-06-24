package com.example.welfare.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestLoggingFilterTest {

    private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(new ClientFingerprintService());

    @Test
    @DisplayName("API 요청에는 request id 응답 헤더를 추가하고 체인을 실행한다")
    void apiRequestAddsRequestIdHeader() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/policies");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChain(204));

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("클라이언트 request id는 안전 문자만 남기고 응답 헤더로 돌려준다")
    void requestIdHeaderIsSanitized() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/policies");
        request.addHeader("X-Request-Id", "abc 123/한글");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChain(200));

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc_123___");
    }

    @Test
    @DisplayName("API가 아닌 요청은 request id 헤더를 추가하지 않는다")
    void nonApiRequestSkipsFilter() throws ServletException, IOException {
        MockHttpServletRequest request = request("/assets/app.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChain(200));

        assertThat(response.getHeader("X-Request-Id")).isNull();
    }

    @Test
    @DisplayName("errorCode attribute는 공통 키로 저장하고 읽는다")
    void observabilityErrorCodeAttributeRoundTrip() {
        MockHttpServletRequest request = request("/api/policies");

        ObservabilityAttributes.setErrorCode(request, "C001");

        assertThat(ObservabilityAttributes.getErrorCode(request)).isEqualTo("C001");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "test-agent");
        return request;
    }

    private FilterChain statusChain(int status) {
        return (request, response) -> ((jakarta.servlet.http.HttpServletResponse) response).setStatus(status);
    }
}
