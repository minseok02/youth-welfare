package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserMetadataUserKeyBackfillServiceTest {

    @Mock
    private UserAttributeRepository userAttributeRepository;

    @Mock
    private UserPriorityRepository userPriorityRepository;

    @InjectMocks
    private UserMetadataUserKeyBackfillService userMetadataUserKeyBackfillService;

    @Test
    @DisplayName("metadata user_key 백필은 attribute와 priority 누락 row를 함께 채운다")
    void backfillMissingUserKeys() {
        given(userAttributeRepository.countMissingUserKeys()).willReturn(3);
        given(userPriorityRepository.countMissingUserKeys()).willReturn(2);
        given(userAttributeRepository.backfillMissingUserKeys()).willReturn(3);
        given(userPriorityRepository.backfillMissingUserKeys()).willReturn(2);

        UserMetadataUserKeyBackfillResponse response = userMetadataUserKeyBackfillService.backfillMissingUserKeys();

        assertThat(response.processedCount()).isEqualTo(5);
        assertThat(response.updatedRowCount()).isEqualTo(5);
        assertThat(response.attributeUpdatedCount()).isEqualTo(3);
        assertThat(response.priorityUpdatedCount()).isEqualTo(2);
    }
}
