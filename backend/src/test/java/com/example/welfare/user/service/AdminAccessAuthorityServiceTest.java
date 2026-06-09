package com.example.welfare.user.service;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.user.entity.AuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AdminAccessAuthorityServiceTest {

    @Mock
    private AuthIdentityReadService authIdentityReadService;

    @Mock
    private AuthAdminRoleService authAdminRoleService;

    @Test
    @DisplayName("현재 allowlist에 남아 있는 admin token은 ROLE_ADMIN을 유지한다")
    void keepsAdminAuthorityWhenCurrentAllowlistStillMatches() {
        AdminAccessAuthorityService service = new AdminAccessAuthorityService(
                authIdentityReadService,
                authAdminRoleService
        );
        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-1")
                .emailLookupHash("admin-email-hash")
                .isActive(true)
                .build();
        given(authIdentityReadService.findByUserKey("user-key-1")).willReturn(Optional.of(authUser));
        given(authAdminRoleService.resolveRolesByEmailLookupHash("admin-email-hash"))
                .willReturn(List.of("ROLE_USER", "ROLE_ADMIN"));

        var result = service.filterCurrentAuthorities(
                new AuthenticatedUser(1L, "user-key-1"),
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(result)
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("allowlist에서 제거된 admin token은 ROLE_ADMIN을 잃는다")
    void removesAdminAuthorityWhenAllowlistNoLongerMatches() {
        AdminAccessAuthorityService service = new AdminAccessAuthorityService(
                authIdentityReadService,
                authAdminRoleService
        );
        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-1")
                .emailLookupHash("old-admin-email-hash")
                .isActive(true)
                .build();
        given(authIdentityReadService.findByUserKey("user-key-1")).willReturn(Optional.of(authUser));
        given(authAdminRoleService.resolveRolesByEmailLookupHash("old-admin-email-hash"))
                .willReturn(List.of("ROLE_USER"));

        var result = service.filterCurrentAuthorities(
                new AuthenticatedUser(1L, "user-key-1"),
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(result)
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("ROLE_ADMIN이 없는 token은 추가 조회 없이 그대로 반환한다")
    void nonAdminAuthoritySkipsCurrentAdminLookup() {
        AdminAccessAuthorityService service = new AdminAccessAuthorityService(
                authIdentityReadService,
                authAdminRoleService
        );

        var result = service.filterCurrentAuthorities(
                new AuthenticatedUser(1L, "user-key-1"),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertThat(result)
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
        then(authIdentityReadService).shouldHaveNoInteractions();
        then(authAdminRoleService).shouldHaveNoInteractions();
    }
}
