package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.repository.UserLegacyPiiSourceReadModel;
import com.example.welfare.user.repository.UserPiiBackfillReadRepository;
import com.example.welfare.user.repository.UserPiiBackfillStateReadModel;
import com.example.welfare.user.repository.UserPiiReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserPiiBackfillServiceTest {

    @Mock
    private UserPiiBackfillReadRepository userPiiBackfillReadRepository;

    @Mock
    private UserPiiCommandService userPiiCommandService;

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Mock
    private AesEncryptUtil aesEncryptUtil;

    @Test
    @DisplayName("백필은 비어 있는 암호화 필드만 앱 레벨 암호화로 채운다")
    void backfillMissingEncryptedFields() {
        UserPiiBackfillService service = new UserPiiBackfillService(
                userPiiBackfillReadRepository,
                userPiiCommandService,
                userPiiSyncQueueService,
                aesEncryptUtil
        );

        UserPiiBackfillStateReadModel state = new UserPiiBackfillStateReadModel(
                "user-key-1",
                null,
                "",
                null
        );
        UserLegacyPiiSourceReadModel source = new StubSource(
                "user-key-1",
                "user@example.com",
                "홍길동",
                LocalDate.of(1998, 1, 10)
        );

        given(userPiiBackfillReadRepository.findMissingEncryptedFields()).willReturn(List.of(state));
        given(userPiiBackfillReadRepository.findLegacySourceByUserKeys(List.of("user-key-1")))
                .willReturn(Map.of("user-key-1", source));
        given(aesEncryptUtil.encrypt("user@example.com")).willReturn("enc-email");
        given(aesEncryptUtil.encrypt("홍길동")).willReturn("enc-name");
        given(aesEncryptUtil.encrypt("1998-01-10")).willReturn("enc-birth");

        UserPiiBackfillResponse result = service.backfillMissingEncryptedFields();

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.updatedUserCount()).isEqualTo(1);
        assertThat(result.emailBackfilledCount()).isEqualTo(1);
        assertThat(result.nameBackfilledCount()).isEqualTo(1);
        assertThat(result.birthDateBackfilledCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        then(userPiiCommandService).should()
                .backfillEncryptedFields("user-key-1", "enc-email", "enc-name", "enc-birth");
    }

    @Test
    @DisplayName("원본 값이 이미 scrub 된 사용자는 건너뛴다")
    void skipWhenLegacySourceAlreadyScrubbed() {
        UserPiiBackfillService service = new UserPiiBackfillService(
                userPiiBackfillReadRepository,
                userPiiCommandService,
                userPiiSyncQueueService,
                aesEncryptUtil
        );

        UserPiiBackfillStateReadModel state = new UserPiiBackfillStateReadModel(
                "user-key-2",
                "enc-email",
                null,
                null
        );
        UserLegacyPiiSourceReadModel source = new StubSource(
                "user-key-2",
                "withdrawn_2",
                null,
                null
        );

        given(userPiiBackfillReadRepository.findMissingEncryptedFields()).willReturn(List.of(state));
        given(userPiiBackfillReadRepository.findLegacySourceByUserKeys(List.of("user-key-2")))
                .willReturn(Map.of("user-key-2", source));

        UserPiiBackfillResponse result = service.backfillMissingEncryptedFields();

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.updatedUserCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        then(userPiiBackfillReadRepository).should().findMissingEncryptedFields();
        then(userPiiCommandService).should(never())
                .backfillEncryptedFields(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("legacy 암호문 회전은 user_pii와 sync queue payload를 v2 암호문으로 재저장한다")
    void rotateLegacyEncryptedFields() {
        UserPiiBackfillService service = new UserPiiBackfillService(
                userPiiBackfillReadRepository,
                userPiiCommandService,
                userPiiSyncQueueService,
                aesEncryptUtil
        );
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder()
                .userKey("user-key-1")
                .build();
        queue.enqueue("legacy-email", "v2:current-name", null, "legacy-phone");

        given(userPiiBackfillReadRepository.findLegacyEncryptedFields())
                .willReturn(List.of(new UserPiiReadModel(
                        "user-key-1",
                        "legacy-email",
                        "v2:current-name",
                        "legacy-birth",
                        null
                )));
        given(userPiiSyncQueueService.findLegacyEncryptedPayloads()).willReturn(List.of(queue));
        given(aesEncryptUtil.isCurrentCipherText("legacy-email")).willReturn(false);
        given(aesEncryptUtil.isCurrentCipherText("v2:current-name")).willReturn(true);
        given(aesEncryptUtil.isCurrentCipherText("legacy-birth")).willReturn(false);
        given(aesEncryptUtil.isCurrentCipherText("legacy-phone")).willReturn(false);
        given(aesEncryptUtil.decrypt("legacy-email")).willReturn("user@example.com");
        given(aesEncryptUtil.decrypt("legacy-birth")).willReturn("1998-01-10");
        given(aesEncryptUtil.decrypt("legacy-phone")).willReturn("01012345678");
        given(aesEncryptUtil.encrypt("user@example.com")).willReturn("v2:new-email");
        given(aesEncryptUtil.encrypt("1998-01-10")).willReturn("v2:new-birth");
        given(aesEncryptUtil.encrypt("01012345678")).willReturn("v2:new-phone");

        var response = service.rotateLegacyEncryptedFields();

        assertThat(response.userPiiProcessedCount()).isEqualTo(1);
        assertThat(response.userPiiUpdatedCount()).isEqualTo(1);
        assertThat(response.queueProcessedCount()).isEqualTo(1);
        assertThat(response.queueUpdatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        then(userPiiCommandService).should()
                .upsertUserPii("user-key-1", "v2:new-email", "v2:current-name", "v2:new-birth", null);
        then(userPiiSyncQueueService).should().save(queue);
        assertThat(queue.getEmailEnc()).isEqualTo("v2:new-email");
        assertThat(queue.getNameEnc()).isEqualTo("v2:current-name");
        assertThat(queue.getBirthDateEnc()).isNull();
        assertThat(queue.getPhoneEnc()).isEqualTo("v2:new-phone");
    }

    private static final class StubSource implements UserLegacyPiiSourceReadModel {
        private final String userKey;
        private final String email;
        private final String name;
        private final LocalDate birthDate;

        private StubSource(String userKey, String email, String name, LocalDate birthDate) {
            this.userKey = userKey;
            this.email = email;
            this.name = name;
            this.birthDate = birthDate;
        }

        @Override
        public String getUserKey() {
            return userKey;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public LocalDate getBirthDate() {
            return birthDate;
        }
    }
}
