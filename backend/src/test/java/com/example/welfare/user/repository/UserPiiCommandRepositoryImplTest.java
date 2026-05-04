package com.example.welfare.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiCommandRepositoryImplTest {

    @Mock
    private UserPiiReadWriteRepository userPiiReadWriteRepository;

    @InjectMocks
    private UserPiiCommandRepositoryImpl userPiiCommandRepository;

    @Test
    @DisplayName("user pii command repository는 backfill write를 위임한다")
    void backfillDelegates() {
        userPiiCommandRepository.backfillEncryptedFields("user-key-1", "enc-email", "enc-name", "enc-birth");

        then(userPiiReadWriteRepository).should()
                .backfillEncryptedFields("user-key-1", "enc-email", "enc-name", "enc-birth");
    }

    @Test
    @DisplayName("user pii command repository는 upsert write를 위임한다")
    void upsertDelegates() {
        userPiiCommandRepository.upsertUserPii("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");

        then(userPiiReadWriteRepository).should()
                .upsertUserPii("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");
    }

    @Test
    @DisplayName("user pii command repository는 delete write를 위임한다")
    void deleteDelegates() {
        userPiiCommandRepository.deleteByUserKey("user-key-1");

        then(userPiiReadWriteRepository).should().deleteByUserKey("user-key-1");
    }
}
