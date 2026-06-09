package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.AsyncCollectStatusResponse;
import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.repository.ApiSyncLogRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectAsyncJobServiceTest {

    @Mock
    private CollectAdminService collectAdminService;

    @Mock
    private ApiSyncLogRepository apiSyncLogRepository;

    @Test
    @DisplayName("비동기 수집 트리거는 queued 상태를 반환하고 executor에서 실제 수집을 실행한다")
    void triggerQueuesAsyncCollect() {
        DeferredExecutor executor = new DeferredExecutor();
        CollectAsyncJobService service = new CollectAsyncJobService(collectAdminService, apiSyncLogRepository, executor);
        doNothing().when(collectAdminService).collect(CollectSource.GOV24);

        AsyncCollectStatusResponse queued = service.trigger(CollectSource.GOV24);

        assertThat(queued.state()).isEqualTo(AsyncCollectStatusResponse.AsyncCollectState.QUEUED);
        assertThat(queued.active()).isTrue();

        executor.runNext();

        AsyncCollectStatusResponse completed = service.getStatus(CollectSource.GOV24);
        assertThat(completed.state()).isEqualTo(AsyncCollectStatusResponse.AsyncCollectState.SUCCEEDED);
        assertThat(completed.active()).isFalse();
        verify(collectAdminService).collect(CollectSource.GOV24);
    }

    @Test
    @DisplayName("활성 비동기 수집이 있으면 같은 lane의 재트리거를 막는다")
    void triggerRejectsWhenActiveJobExists() {
        DeferredExecutor executor = new DeferredExecutor();
        CollectAsyncJobService service = new CollectAsyncJobService(collectAdminService, apiSyncLogRepository, executor);

        service.trigger(CollectSource.GOV24);

        assertThatThrownBy(() -> service.trigger(CollectSource.YOUTH))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COLLECT_ALREADY_RUNNING);
    }

    @Test
    @DisplayName("비동기 수집 실패는 failed 상태와 errorCode를 남긴다")
    void triggerMarksFailureWhenCollectFails() {
        Executor directExecutor = Runnable::run;
        CollectAsyncJobService service = new CollectAsyncJobService(collectAdminService, apiSyncLogRepository, directExecutor);
        doThrow(new CustomException(ErrorCode.COLLECT_ALREADY_RUNNING)).when(collectAdminService).collect(CollectSource.GOV24);

        AsyncCollectStatusResponse response = service.trigger(CollectSource.GOV24);

        assertThat(response.state()).isEqualTo(AsyncCollectStatusResponse.AsyncCollectState.FAILED);
        assertThat(response.errorCode()).isEqualTo(ErrorCode.COLLECT_ALREADY_RUNNING.getCode());
        assertThat(response.errorMessage()).contains("errorCode=" + ErrorCode.COLLECT_ALREADY_RUNNING.getCode());
    }

    @Test
    @DisplayName("비동기 수집 실패 응답은 원문 예외 메시지를 노출하지 않는다")
    void triggerFailureDoesNotExposeRawExceptionMessage() {
        Executor directExecutor = Runnable::run;
        CollectAsyncJobService service = new CollectAsyncJobService(collectAdminService, apiSyncLogRepository, directExecutor);
        doThrow(new IllegalStateException("https://apis.data.go.kr/path?serviceKey=secret-key"))
                .when(collectAdminService).collect(CollectSource.GOV24);

        AsyncCollectStatusResponse response = service.trigger(CollectSource.GOV24);

        assertThat(response.state()).isEqualTo(AsyncCollectStatusResponse.AsyncCollectState.FAILED);
        assertThat(response.errorCode()).isEqualTo("IllegalStateException");
        assertThat(response.errorMessage())
                .contains("errorCode=IllegalStateException")
                .doesNotContain("serviceKey")
                .doesNotContain("secret-key");
    }

    @Test
    @DisplayName("상태 조회는 최신 api sync log snapshot을 같이 반환한다")
    void getStatusIncludesLatestApiSyncLog() {
        Executor directExecutor = Runnable::run;
        CollectAsyncJobService service = new CollectAsyncJobService(collectAdminService, apiSyncLogRepository, directExecutor);
        ApiSyncLog log = ApiSyncLog.start("GOV24");
        log.complete(CollectResult.withMetadata(10, 10, 0, 0, 0, "{\"chunkCount\":2}"));
        given(apiSyncLogRepository.findTopByJobNameOrderByIdDesc("GOV24")).willReturn(Optional.of(log));

        AsyncCollectStatusResponse response = service.getStatus(CollectSource.GOV24);

        assertThat(response.latestLog()).isNotNull();
        assertThat(response.latestLog().requestedCount()).isEqualTo(10);
        assertThat(response.latestLog().metadataJson()).contains("\"chunkCount\":2");
    }

    private static final class DeferredExecutor implements Executor {
        private Runnable next;

        @Override
        public void execute(Runnable command) {
            this.next = command;
        }

        void runNext() {
            if (next == null) {
                throw new IllegalStateException("no queued task");
            }
            Runnable command = next;
            next = null;
            command.run();
        }
    }
}
