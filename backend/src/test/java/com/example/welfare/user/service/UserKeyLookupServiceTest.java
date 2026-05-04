package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.repository.UserKeyReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserKeyLookupServiceTest {

    @Mock
    private UserKeyReadRepository userKeyReadRepository;

    @InjectMocks
    private UserKeyLookupService userKeyLookupService;

    @Test
    @DisplayName("nullable lookup은 user id가 없으면 null을 반환한다")
    void findNullableReturnsNullForMissingId() {
        assertThat(userKeyLookupService.findNullable(null)).isNull();
    }

    @Test
    @DisplayName("nullable lookup은 read repository 결과를 그대로 반환한다")
    void findNullableDelegates() {
        given(userKeyReadRepository.findUserKeyById(7L)).willReturn(Optional.of("user-key-7"));

        assertThat(userKeyLookupService.findNullable(7L)).isEqualTo("user-key-7");
    }

    @Test
    @DisplayName("required lookup은 user id가 없으면 USER_NOT_FOUND를 던진다")
    void findRequiredRejectsNullId() {
        assertThatThrownBy(() -> userKeyLookupService.findRequired(null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("required lookup은 read repository 결과를 반환한다")
    void findRequiredDelegates() {
        given(userKeyReadRepository.findUserKeyById(7L)).willReturn(Optional.of("user-key-7"));

        assertThat(userKeyLookupService.findRequired(7L)).isEqualTo("user-key-7");
    }

    @Test
    @DisplayName("required user id by userKey 조회는 read repository 결과를 반환한다")
    void requireExistingUserIdByUserKeyDelegates() {
        given(userKeyReadRepository.findIdByUserKey("user-key-11")).willReturn(Optional.of(11L));

        assertThat(userKeyLookupService.requireExistingUserIdByUserKey("user-key-11")).isEqualTo(11L);
    }
}
