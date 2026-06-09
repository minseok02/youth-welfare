package com.example.welfare.user.service;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.user.entity.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAccessAuthorityService {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final AuthIdentityReadService authIdentityReadService;
    private final AuthAdminRoleService authAdminRoleService;

    public List<GrantedAuthority> filterCurrentAuthorities(AuthenticatedUser authenticatedUser,
                                                           Collection<? extends GrantedAuthority> tokenAuthorities) {
        List<GrantedAuthority> authorities = List.copyOf(tokenAuthorities);
        boolean tokenHasAdmin = authorities.stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        if (!tokenHasAdmin) {
            return authorities;
        }

        if (isCurrentAdmin(authenticatedUser)) {
            return authorities;
        }

        return authorities.stream()
                .filter(authority -> !ADMIN_AUTHORITY.equals(authority.getAuthority()))
                .toList();
    }

    private boolean isCurrentAdmin(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || !StringUtils.hasText(authenticatedUser.userKey())) {
            return false;
        }

        return authIdentityReadService.findByUserKey(authenticatedUser.userKey())
                .filter(AuthUser::isActive)
                .map(AuthUser::getEmailLookupHash)
                .map(authAdminRoleService::resolveRolesByEmailLookupHash)
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }
}
