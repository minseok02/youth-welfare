package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.repository.UserLegacyPiiSourceReadModel;
import com.example.welfare.user.repository.UserPiiBackfillReadRepository;
import com.example.welfare.user.repository.UserPiiBackfillStateReadModel;
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
    private AesEncryptUtil aesEncryptUtil;

    @Test
    @DisplayName("백필은 비어 있는 암호화 필드만 앱 레벨 암호화로 채운다")
    void backfillMissingEncryptedFields() {
        UserPiiBackfillService service = new UserPiiBackfillService(userPiiBackfillReadRepository, userPiiCommandService, aesEncryptUtil);

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
        UserPiiBackfillService service = new UserPiiBackfillService(userPiiBackfillReadRepository, userPiiCommandService, aesEncryptUtil);

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
