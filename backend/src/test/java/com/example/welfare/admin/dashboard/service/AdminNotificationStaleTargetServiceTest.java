package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleTargetResponse;
import com.example.welfare.admin.dashboard.repository.AdminNotificationStaleTargetReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AdminNotificationStaleTargetServiceTest {

    @Mock
    private AdminNotificationStaleTargetReadRepository readRepository;

    @InjectMocks
    private AdminNotificationStaleTargetService service;

    @Test
    @DisplayName("stale notification target 요약과 recent items를 반환한다")
    void returnsStaleTargetSummary() {
        LocalDateTime oldest = LocalDateTime.parse("2026-05-16T05:55:31");
        LocalDateTime newest = LocalDateTime.parse("2026-05-17T04:13:16");
        given(readRepository.countStaleRows(org.mockito.ArgumentMatchers.any())).willReturn(9L);
        given(readRepository.countStaleGroups(org.mockito.ArgumentMatchers.any())).willReturn(3L);
        given(readRepository.findTargets(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(5)))
                .willReturn(List.of(new AdminNotificationStaleTargetReadRepository.NotificationStaleTargetRow(
                        "DEADLINE_REMINDER",
                        "북마크한 정책 마감이 임박했어요",
                        "/policies/2622",
                        5,
                        5,
                        oldest,
                        newest
                )));

        AdminNotificationStaleTargetResponse response = service.getRecentTargets(5, 14);

        assertThat(response.olderThanDays()).isEqualTo(14);
        assertThat(response.staleRowCount()).isEqualTo(9);
        assertThat(response.staleGroupCount()).isEqualTo(3);
        assertThat(response.recentTargets()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo("DEADLINE_REMINDER");
            assertThat(item.title()).isEqualTo("북마크한 정책 마감이 임박했어요");
            assertThat(item.deeplinkUrl()).isEqualTo("/policies/2622");
            assertThat(item.rowCount()).isEqualTo(5);
            assertThat(item.userCount()).isEqualTo(5);
        });

        then(readRepository).should().findTargets(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(5));
    }
}
