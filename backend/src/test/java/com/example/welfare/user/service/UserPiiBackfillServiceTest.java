package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.repository.UserPiiBackfillTarget;
import com.example.welfare.user.repository.UserPiiRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiBackfillServiceTest {

    @Mock
    private UserPiiRepository userPiiRepository;

    @Mock
    private UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Mock
    private AesEncryptUtil aesEncryptUtil;

    @Test
    @DisplayName("백필은 비어 있는 암호화 필드만 앱 레벨 암호화로 채운다")
    void backfillMissingEncryptedFields() {
        UserPiiBackfillService service = new UserPiiBackfillService(userPiiRepository, userPiiReadWriteRepository, aesEncryptUtil);

        UserPiiBackfillTarget target = new StubTarget(
                "user-key-1",
                "user@example.com",
                "홍길동",
                LocalDate.of(1998, 1, 10),
                null,
                "",
                null
        );

        given(userPiiRepository.findBackfillTargets()).willReturn(List.of(target));
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
        then(userPiiReadWriteRepository).should()
                .backfillEncryptedFields("user-key-1", "enc-email", "enc-name", "enc-birth");
    }

    @Test
    @DisplayName("원본 값이 이미 scrub 된 사용자는 건너뛴다")
    void skipWhenLegacySourceAlreadyScrubbed() {
        UserPiiBackfillService service = new UserPiiBackfillService(userPiiRepository, userPiiReadWriteRepository, aesEncryptUtil);

        UserPiiBackfillTarget target = new StubTarget(
                "user-key-2",
                "withdrawn_2",
                null,
                null,
                "enc-email",
                null,
                null
        );

        given(userPiiRepository.findBackfillTargets()).willReturn(List.of(target));

        UserPiiBackfillResponse result = service.backfillMissingEncryptedFields();

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.updatedUserCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        then(userPiiRepository).should().findBackfillTargets();
        then(userPiiReadWriteRepository).shouldHaveNoInteractions();
    }

    private static final class StubTarget implements UserPiiBackfillTarget {
        private final String userKey;
        private final String email;
        private final String name;
        private final LocalDate birthDate;
        private final String emailEnc;
        private final String nameEnc;
        private final String birthDateEnc;

        private StubTarget(String userKey, String email, String name, LocalDate birthDate,
                           String emailEnc, String nameEnc, String birthDateEnc) {
            this.userKey = userKey;
            this.email = email;
            this.name = name;
            this.birthDate = birthDate;
            this.emailEnc = emailEnc;
            this.nameEnc = nameEnc;
            this.birthDateEnc = birthDateEnc;
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

        @Override
        public String getEmailEnc() {
            return emailEnc;
        }

        @Override
        public String getNameEnc() {
            return nameEnc;
        }

        @Override
        public String getBirthDateEnc() {
            return birthDateEnc;
        }
    }
}
