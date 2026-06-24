package com.example.welfare.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedOriginFilterTest {

    private final TrustedOriginFilter filter = new TrustedOriginFilter(
            "https://youthmoa.kr,http://127.0.0.1:5173",
            new ObjectMapper()
    );

    @Test
    @DisplayName("보호된 POST는 허용 origin이면 통과한다")
    void protectedPostAllowsTrustedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = post("/api/auth/refresh");
        request.addHeader(HttpHeaders.ORIGIN, "https://youthmoa.kr");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("보호된 POST는 다른 origin이면 403을 반환한다")
    void protectedPostRejectsUntrustedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = post("/api/auth/logout");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"C003\"");
    }

    @Test
    @DisplayName("보호된 POST는 origin/referer가 모두 없으면 403을 반환한다")
    void protectedPostRejectsRequestWithoutBrowserOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = post("/api/notifications/unsubscribe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"C003\"");
    }

    @Test
    @DisplayName("보호 대상이 아닌 POST는 origin 검증 대상이 아니다")
    void nonProtectedPostSkipsOriginCheck() throws ServletException, IOException {
        MockHttpServletRequest request = post("/api/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        return request;
    }
}
