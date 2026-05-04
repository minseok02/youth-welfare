package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.SearchLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PolicySearchLogCommandRepositoryImplTest {

    @Mock
    private SearchLogRepository searchLogRepository;

    @InjectMocks
    private PolicySearchLogCommandRepositoryImpl policySearchLogCommandRepository;

    @Test
    @DisplayName("policy search log command repository는 검색 로그 저장을 위임한다")
    void saveDelegates() {
        SearchLog searchLog = SearchLog.builder().keyword("월세 지원").build();

        policySearchLogCommandRepository.save(searchLog);

        then(searchLogRepository).should().save(searchLog);
    }
}
