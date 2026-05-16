package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.UserPiiReadModel;
import com.example.welfare.user.repository.UserProfileAggregateReadModel;
import com.example.welfare.user.repository.UserProfileReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileReadServiceTest {

    @Mock private UserProfileReadRepository userProfileReadRepository;
    @Mock private AesEncryptUtil aesEncryptUtil;
    @Mock private UserKeyLookupService userKeyLookupService;
    @Mock private AuthIdentityReadService authIdentityReadService;

    @Test
    @DisplayName("프로필 조회는 app_pii_rw 저장소에서 PII 암호문을 읽는다")
    void getProfileLoadsPiiFromAppPiiReadWriteRepository() {
        UserProfileReadService userProfileReadService = new UserProfileReadService(
                userProfileReadRepository,
                aesEncryptUtil,
                userKeyLookupService,
                authIdentityReadService
        );
        UserProfile profile = UserProfile.builder()
                .userKey("user-key-1")
                .sido("서울특별시")
                .sgg("관악구")
                .ageBand("25-29")
                .notificationYn(true)
                .notificationEmailYn(true)
                .notificationInAppYn(true)
                .notificationWebPushYn(false)
                .notificationConsentAt(LocalDateTime.of(2026, 5, 1, 9, 30))
                .hasPhone(true)
                .displayCount(12)
                .build();

        when(userKeyLookupService.findRequired(1L)).thenReturn("user-key-1");
        when(authIdentityReadService.requireActiveUserKey("user-key-1")).thenReturn("user-key-1");
        when(userProfileReadRepository.findProfileAggregateByUserKey("user-key-1"))
                .thenReturn(Optional.of(new UserProfileAggregateReadModel(
                        profile,
                        new UserPiiReadModel("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone"),
                        List.of(),
                        List.of()
                )));
        when(aesEncryptUtil.decrypt("enc-email")).thenReturn("user@example.com");
        when(aesEncryptUtil.decrypt("enc-name")).thenReturn("홍길동");
        when(aesEncryptUtil.decrypt("enc-birth")).thenReturn("1999-01-10");

        ProfileResponse response = userProfileReadService.getProfile(1L);

        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getBirthDate()).isEqualTo(LocalDate.of(1999, 1, 10));
        assertThat(response.getAgeBand()).isEqualTo("25-29");
        assertThat(response.isNotificationYn()).isTrue();
        assertThat(response.isNotificationEmailYn()).isTrue();
        assertThat(response.isNotificationInAppYn()).isTrue();
        assertThat(response.isNotificationWebPushYn()).isFalse();
        assertThat(response.getNotificationConsentAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 9, 30));
        assertThat(response.isHasPhone()).isTrue();
        verify(userProfileReadRepository).findProfileAggregateByUserKey("user-key-1");
    }
}
