package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.repository.PriorityOptionReadRepository;
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
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PriorityOptionReadServiceTest {

    @Mock
    private PriorityOptionReadRepository priorityOptionReadRepository;

    @InjectMocks
    private PriorityOptionReadService priorityOptionReadService;

    @Test
    @DisplayName("priority option read service는 code로 option을 조회한다")
    void requireByCodeReturnsOption() {
        PriorityOption option = mock(PriorityOption.class);
        given(priorityOptionReadRepository.findByCode("HOUSING")).willReturn(Optional.of(option));

        assertThat(priorityOptionReadService.requireByCode("HOUSING")).isSameAs(option);
    }

    @Test
    @DisplayName("priority option read service는 없는 code면 invalid input을 던진다")
    void requireByCodeThrowsWhenMissing() {
        given(priorityOptionReadRepository.findByCode("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> priorityOptionReadService.requireByCode("UNKNOWN"))
                .isInstanceOf(CustomException.class);
    }
}
