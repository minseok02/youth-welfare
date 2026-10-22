package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static com.example.welfare.collect.service.CollectBatchRunResult.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectBatchRunResultTest {

    @Test
    void partiallyFailedItemsKeepCountsButDoNotClaimSourceSuccess() {
        CollectResult items = CollectResult.of(50, 48, 0, 0, 2);
        var source = CollectBatchRunResult.SourceRunResult.completed(CollectSource.GOV24_SUPPORT_CONDITIONS, items);
        var batch = new CollectBatchRunResult(List.of(source));

        assertThat(source.success()).isFalse();
        assertThat(source.status()).isEqualTo(PARTIAL_SUCCESS);
        assertThat(source.result()).isSameAs(items);
        assertThat(batch.status()).isEqualTo(PARTIAL_SUCCESS);
        assertThat(batch.completedWithFailures()).isTrue();
        assertThat(batch.succeededSourceCount()).isZero();
        assertThat(batch.partialSourceCount()).isEqualTo(1);
        assertThat(batch.failedSourceCount()).isZero();
    }

    @Test
    void allFailedItemsAreFailedRatherThanPartialSuccess() {
        var source = CollectBatchRunResult.SourceRunResult.completed(CollectSource.GOV24, CollectResult.of(2, 0, 0, 0, 2));
        var batch = new CollectBatchRunResult(List.of(source));

        assertThat(source.success()).isFalse();
        assertThat(source.status()).isEqualTo(FAILED);
        assertThat(source.result().failedCount()).isEqualTo(2);
        assertThat(batch.status()).isEqualTo(FAILED);
        assertThat(batch.completedWithFailures()).isTrue();
        assertThat(batch.succeededSourceCount()).isZero();
        assertThat(batch.partialSourceCount()).isZero();
        assertThat(batch.failedSourceCount()).isEqualTo(1);
    }

    @Test
    void thrownFailureDoesNotInventUnknownItemCounts() {
        var source = CollectBatchRunResult.SourceRunResult.failure(CollectSource.YOUTH,
                new CustomException(ErrorCode.COLLECT_API_FAILED));
        var batch = new CollectBatchRunResult(List.of(source));

        assertThat(source.status()).isEqualTo(FAILED);
        assertThat(source.errorCode()).isEqualTo("COL001");
        assertThat(source.result()).isEqualTo(CollectResult.of(0, 0, 0, 0, 0));
        assertThat(batch.status()).isEqualTo(FAILED);
        assertThat(batch.failedSourceCount()).isEqualTo(1);
        assertThat(batch.completedWithFailures()).isTrue();
    }

    @Test
    void skippedAndFilteredItemsWithoutFailuresAreSuccessfulCompletion() {
        CollectResult items = CollectResult.withMetadata(10, 0, 7, 3, 0, "{\"fixture\":true}");
        var source = CollectBatchRunResult.SourceRunResult.completed(CollectSource.BOKJIRO_LOCAL, items);
        var batch = new CollectBatchRunResult(List.of(source));

        assertThat(source.status()).isEqualTo(SUCCESS);
        assertThat(source.success()).isTrue();
        assertThat(source.result()).isSameAs(items);
        assertThat(batch.status()).isEqualTo(SUCCESS);
        assertThat(batch.completedWithFailures()).isFalse();
        assertThat(batch.succeededSourceCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"1,0", "0,1"})
    void nonfailedSkippedOrFilteredItemsStillDistinguishPartialFromTotalFailure(int skipped, int filtered) {
        var source = CollectBatchRunResult.SourceRunResult.completed(CollectSource.BOKJIRO_LOCAL,
                CollectResult.of(2, 0, skipped, filtered, 1));

        assertThat(source.success()).isFalse();
        assertThat(source.status()).isEqualTo(PARTIAL_SUCCESS);
    }

    @Test
    void sourceSuccessAndExceptionFailureProducePartialBatchWithDisjointCounts() {
        var batch = new CollectBatchRunResult(List.of(
                CollectBatchRunResult.SourceRunResult.completed(CollectSource.YOUTH, CollectResult.of(10, 10, 0, 0, 0)),
                CollectBatchRunResult.SourceRunResult.failure(CollectSource.BOKJIRO_LOCAL, new IllegalStateException())
        ));

        assertThat(batch.status()).isEqualTo(PARTIAL_SUCCESS);
        assertThat(batch.requestedSourceCount()).isEqualTo(2);
        assertThat(batch.succeededSourceCount()).isEqualTo(1);
        assertThat(batch.partialSourceCount()).isZero();
        assertThat(batch.failedSourceCount()).isEqualTo(1);
        assertCountsCoverEverySource(batch);
    }

    @Test
    void listDiffFailureIsSeparateFromSuccessfulSourceStorageAndSanitizesCause() {
        var diff = CollectBatchRunResult.ListDiffRunResult.failure(CollectSource.GOV24,
                new IllegalStateException("internal diagnostic must not reach the response"));
        var batch = new CollectBatchRunResult(List.of(
                CollectBatchRunResult.SourceRunResult.completed(CollectSource.GOV24, CollectResult.of(10, 10, 0, 0, 0))
        ), List.of(diff));

        assertThat(batch.status()).isEqualTo(PARTIAL_SUCCESS);
        assertThat(batch.completedWithFailures()).isTrue();
        assertThat(batch.succeededSourceCount()).isEqualTo(1);
        assertThat(batch.failedSourceCount()).isZero();
        assertThat(batch.failedListDiffCount()).isEqualTo(1);
        assertThat(diff.status()).isEqualTo(FAILED);
        assertThat(diff.reason()).isEqualTo("LIST_DIFF_FAILED");
        assertThat(diff.errorCode()).isEqualTo("IllegalStateException");
        assertThat(diff.errorMessage()).isEqualTo("list diff failed; see application logs with errorCode=IllegalStateException");
    }

    @Test
    void skippedListDiffDoesNotCountTheSourceFailureTwice() {
        var batch = new CollectBatchRunResult(List.of(
                CollectBatchRunResult.SourceRunResult.completed(CollectSource.GOV24, CollectResult.of(50, 48, 0, 0, 2))
        ), List.of(CollectBatchRunResult.ListDiffRunResult.skipped(CollectSource.GOV24, "SOURCE_PARTIAL_SUCCESS")));

        assertThat(batch.status()).isEqualTo(PARTIAL_SUCCESS);
        assertThat(batch.partialSourceCount()).isEqualTo(1);
        assertThat(batch.failedSourceCount()).isZero();
        assertThat(batch.failedListDiffCount()).isZero();
        assertThat(batch.listDiffResults().get(0).status()).isEqualTo(SKIPPED);
        assertCountsCoverEverySource(batch);
    }

    @Test
    void noSourceExecutionAndOnlySkippedStagesAreExplicitlySkipped() {
        var empty = new CollectBatchRunResult(List.of());
        var skipped = new CollectBatchRunResult(List.of(), List.of(
                CollectBatchRunResult.ListDiffRunResult.skipped(CollectSource.GOV24_DETAIL, "NOT_LIST_SOURCE")
        ));

        for (var batch : List.of(empty, skipped)) {
            assertThat(batch.status()).isEqualTo(SKIPPED);
            assertThat(batch.completedWithFailures()).isFalse();
            assertThat(batch.requestedSourceCount()).isZero();
        }
    }

    @Test
    void duplicateForcedAndRotationLanesRemainSeparateExecutionEntries() {
        var batch = new CollectBatchRunResult(List.of(
                CollectBatchRunResult.SourceRunResult.completed(CollectSource.GOV24_DETAIL, CollectResult.of(2, 1, 0, 0, 1)),
                CollectBatchRunResult.SourceRunResult.completed(CollectSource.GOV24_DETAIL, CollectResult.of(50, 10, 40, 0, 0))
        ));

        assertThat(batch.requestedSourceCount()).isEqualTo(2);
        assertThat(batch.succeededSourceCount()).isEqualTo(1);
        assertThat(batch.partialSourceCount()).isEqualTo(1);
        assertThat(batch.sourceResults()).hasSize(2);
        assertCountsCoverEverySource(batch);
    }

    @Test
    void resultListsAreImmutableSnapshots() {
        var sources = new ArrayList<CollectBatchRunResult.SourceRunResult>();
        var diffs = new ArrayList<CollectBatchRunResult.ListDiffRunResult>();
        var batch = new CollectBatchRunResult(sources, diffs);
        sources.add(CollectBatchRunResult.SourceRunResult.completed(CollectSource.YOUTH, CollectResult.of(1, 1, 0, 0, 0)));
        diffs.add(CollectBatchRunResult.ListDiffRunResult.success(CollectSource.YOUTH, "BASELINE_ONLY"));

        assertThat(batch.sourceResults()).isEmpty();
        assertThat(batch.listDiffResults()).isEmpty();
        assertThatThrownBy(() -> batch.sourceResults().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> batch.listDiffResults().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void directConstructionCannotClaimSuccessWithFailedItems() {
        var source = new CollectBatchRunResult.SourceRunResult(CollectSource.YOUTH, true,
                CollectResult.of(2, 1, 0, 0, 1), null, null);

        assertThat(source.success()).isFalse();
        assertThat(source.status()).isEqualTo(PARTIAL_SUCCESS);
    }

    private static void assertCountsCoverEverySource(CollectBatchRunResult result) {
        assertThat(result.succeededSourceCount() + result.partialSourceCount() + result.failedSourceCount())
                .isEqualTo(result.requestedSourceCount());
    }
}
