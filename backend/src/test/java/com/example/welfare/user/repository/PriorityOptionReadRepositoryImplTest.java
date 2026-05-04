package com.example.welfare.user.repository;

import com.example.welfare.user.entity.PriorityOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PriorityOptionReadRepositoryImplTest {

    @Mock
    private PriorityOptionRepository priorityOptionRepository;

    @InjectMocks
    private PriorityOptionReadRepositoryImpl priorityOptionReadRepository;

    @Test
    @DisplayName("priority option read repository는 code 기반 옵션 조회를 위임한다")
    void findByCodeDelegates() {
        PriorityOption option = mock(PriorityOption.class);
        given(priorityOptionRepository.findByCode("HOUSING")).willReturn(Optional.of(option));

        assertThat(priorityOptionReadRepository.findByCode("HOUSING")).contains(option);
        then(priorityOptionRepository).should().findByCode("HOUSING");
    }
}
