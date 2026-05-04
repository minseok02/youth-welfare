package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReadServiceTest {

    @Mock private ActiveUserReadService activeUserReadService;
    @Mock private UserKeyLookupService userKeyLookupService;

    @Test
    @DisplayName("active user context 조회는 active user read service에 위임한다")
    void getActiveUserContextDelegates() {
        UserReadService userReadService = new UserReadService(
                activeUserReadService,
                userKeyLookupService
        );
        User user = User.builder()
                .id(9L)
                .userKey("user-key-9")
                .build();

        when(activeUserReadService.getActiveUserContext(9L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-9"));

        UserReadService.ActiveUserContext context = userReadService.getActiveUserContext(9L);

        assertThat(context.user()).isEqualTo(user);
        assertThat(context.userKey()).isEqualTo("user-key-9");
    }

    @Test
    @DisplayName("optional active user by userKey 조회는 active user read service에 위임한다")
    void findOptionalActiveUserByUserKeyDelegates() {
        UserReadService userReadService = new UserReadService(
                activeUserReadService,
                userKeyLookupService
        );
        User user = User.builder().id(10L).userKey("user-key-10").build();

        when(activeUserReadService.findOptionalActiveUserByUserKey("user-key-10")).thenReturn(Optional.of(user));

        assertThat(userReadService.findOptionalActiveUserByUserKey("user-key-10")).contains(user);
    }

    @Test
    @DisplayName("required active user by userKey 조회는 active user read service에 위임한다")
    void getActiveUserByUserKeyDelegates() {
        UserReadService userReadService = new UserReadService(
                activeUserReadService,
                userKeyLookupService
        );
        User user = User.builder().id(10L).userKey("user-key-10").build();

        when(activeUserReadService.getActiveUserByUserKey("user-key-10")).thenReturn(user);

        assertThat(userReadService.getActiveUserByUserKey("user-key-10")).isEqualTo(user);
    }

    @Test
    @DisplayName("existing user id by userKey 조회는 userId를 반환한다")
    void requireExistingUserIdByUserKeyReturnsUserId() {
        UserReadService userReadService = new UserReadService(
                activeUserReadService,
                userKeyLookupService
        );

        when(userKeyLookupService.findRequiredUserId("user-key-11")).thenReturn(Optional.of(11L));

        assertThat(userReadService.requireExistingUserIdByUserKey("user-key-11")).isEqualTo(11L);
    }
}
