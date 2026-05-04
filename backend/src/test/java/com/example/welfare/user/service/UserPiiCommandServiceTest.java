package com.example.welfare.user.service;

import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiCommandServiceTest {

    @Mock
    private UserPiiReadWriteRepository userPiiReadWriteRepository;

    @InjectMocks
    private UserPiiCommandService userPiiCommandService;

    @Test
    @DisplayName("backfill write는 app pii 저장소에 위임한다")
    void backfillDelegates() {
        userPiiCommandService.backfillEncryptedFields("user-key-1", "enc-email", "enc-name", "enc-birth");

        then(userPiiReadWriteRepository).should()
                .backfillEncryptedFields("user-key-1", "enc-email", "enc-name", "enc-birth");
    }

    @Test
    @DisplayName("upsert write는 app pii 저장소에 위임한다")
    void upsertDelegates() {
        userPiiCommandService.upsertUserPii("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");

        then(userPiiReadWriteRepository).should()
                .upsertUserPii("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");
    }

    @Test
    @DisplayName("delete write는 app pii 저장소에 위임한다")
    void deleteDelegates() {
        userPiiCommandService.deleteByUserKey("user-key-1");

        then(userPiiReadWriteRepository).should().deleteByUserKey("user-key-1");
    }
}
