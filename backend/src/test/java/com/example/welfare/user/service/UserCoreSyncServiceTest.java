package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.event.UserPiiSyncRequestedEvent;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserCoreSyncServiceTest {

    @Mock
    private UserKeyLookupService userKeyLookupService;

    @Mock
    private UserCoreProjectionSyncService userCoreProjectionSyncService;

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Mock
    private UserPlainPiiReadService userPlainPiiReadService;

    @Mock
    private AuthIdentityReadService authIdentityReadService;

    @Mock
    private AesEncryptUtil aesEncryptUtil;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    @DisplayName("core sync는 auth/profile 저장 후 pii sync queue와 after-commit 이벤트를 적재한다")
    void syncFromUserEnqueuesPiiSync() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("pw-hash")
                .name("Queue User")
                .birthDate(LocalDate.of(1998, 1, 10))
                .phoneEnc("enc-phone")
                .sido("서울특별시")
                .sgg("강남구")
                .incomeLevel((byte) 5)
                .employmentStatus("EMPLOYED")
                .householdType("SINGLE")
                .build();

        given(userKeyLookupService.findRequired(1L)).willReturn("user-key-1");
        given(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .willReturn(new UserPlainPii("user@example.com", "Queue User", LocalDate.of(1998, 1, 10)));
        given(authIdentityReadService.findByUserKey("user-key-1"))
                .willReturn(java.util.Optional.of(
                        AuthUser.builder()
                                .userKey("user-key-1")
                                .emailLookupHash(EmailLookupKeyGenerator.hash("user@example.com"))
                                .build()
                ));
        given(aesEncryptUtil.encrypt("user@example.com")).willReturn("enc-email");
        given(aesEncryptUtil.encrypt("Queue User")).willReturn("enc-name");
        given(aesEncryptUtil.encrypt("1998-01-10")).willReturn("enc-birth");

        UserCoreSyncService service = new UserCoreSyncService(
                userKeyLookupService,
                userCoreProjectionSyncService,
                userPiiSyncQueueService,
                userPlainPiiReadService,
                authIdentityReadService,
                aesEncryptUtil,
                applicationEventPublisher
        );

        service.syncFromUser(user);

        then(userCoreProjectionSyncService).should()
                .syncAuthUser(user, "user-key-1", EmailLookupKeyGenerator.hash("user@example.com"));
        then(userCoreProjectionSyncService).should()
                .syncUserProfile(
                        org.mockito.ArgumentMatchers.same(user),
                        org.mockito.ArgumentMatchers.eq("user-key-1"),
                        org.mockito.ArgumentMatchers.eq(28),
                        org.mockito.ArgumentMatchers.eq("25_29"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(true),
                        org.mockito.ArgumentMatchers.eq(true)
                );

        then(userPiiSyncQueueService).should()
                .enqueue("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");
        then(applicationEventPublisher).should().publishEvent(new UserPiiSyncRequestedEvent("user-key-1"));
    }

    @Test
    @DisplayName("기존 auth identity hash가 있으면 user_pii email 변경과 무관하게 그대로 유지한다")
    void syncFromUserPreservesExistingAuthIdentityHash() {
        User user = User.builder()
                .id(2L)
                .email("user@example.com")
                .passwordHash("pw-hash")
                .build();

        given(userKeyLookupService.findRequired(2L)).willReturn("user-key-2");
        given(userPlainPiiReadService.resolveCurrent(user, "user-key-2"))
                .willReturn(new UserPlainPii("changed-contact@example.com", null, null));
        given(authIdentityReadService.findByUserKey("user-key-2"))
                .willReturn(java.util.Optional.of(
                        AuthUser.builder()
                                .userKey("user-key-2")
                                .emailLookupHash(EmailLookupKeyGenerator.hash("user@example.com"))
                                .build()
                ));
        given(aesEncryptUtil.encrypt("changed-contact@example.com")).willReturn("enc-email");

        UserCoreSyncService service = new UserCoreSyncService(
                userKeyLookupService,
                userCoreProjectionSyncService,
                userPiiSyncQueueService,
                userPlainPiiReadService,
                authIdentityReadService,
                aesEncryptUtil,
                applicationEventPublisher
        );

        service.syncFromUser(user);

        then(userCoreProjectionSyncService).should()
                .syncAuthUser(user, "user-key-2", EmailLookupKeyGenerator.hash("user@example.com"));
        then(aesEncryptUtil).should().encrypt("changed-contact@example.com");
        then(userCoreProjectionSyncService).should(never())
                .syncAuthUser(user, "user-key-2", EmailLookupKeyGenerator.hash("changed-contact@example.com"));
    }
}
