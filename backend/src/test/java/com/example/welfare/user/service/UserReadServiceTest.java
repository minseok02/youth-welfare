package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReadServiceTest {

    @Mock private UserKeyLookupService userKeyLookupService;

    @Test
    @DisplayName("existing user id by userKey 조회는 userId를 반환한다")
    void requireExistingUserIdByUserKeyReturnsUserId() {
        UserReadService userReadService = new UserReadService(userKeyLookupService);

        when(userKeyLookupService.findRequiredUserId("user-key-11")).thenReturn(Optional.of(11L));

        assertThat(userReadService.requireExistingUserIdByUserKey("user-key-11")).isEqualTo(11L);
    }
}
