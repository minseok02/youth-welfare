package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminWrapperObservationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class AdminDashboardWrapperObservationService {

    private static final Path ACTIVE_BASELINE_SUMMARY_PATH = Path.of(
            "tmp",
            "active-baseline-suite",
            "latest-active-baseline-summary.txt"
    );
    private static final Path CURRENT_PRIORITY_SUMMARY_PATH = Path.of(
            "tmp",
            "current-priority-suite",
            "latest-current-priority-summary.txt"
    );

    public AdminWrapperObservationResponse getLatestObservation() {
        return fromSummaryPaths(ACTIVE_BASELINE_SUMMARY_PATH, CURRENT_PRIORITY_SUMMARY_PATH);
    }

    AdminWrapperObservationResponse fromSummaryPaths(Path activeBaselineSummaryPath, Path currentPrioritySummaryPath) {
        SummarySnapshot activeBaseline = readSummary(activeBaselineSummaryPath);
        SummarySnapshot currentPriority = readSummary(currentPrioritySummaryPath);
        SummarySnapshot previousCurrentPriority = readPreviousSummary(
                currentPrioritySummaryPath,
                "current-priority-summary.txt"
        );
        boolean currentPriorityPreviousAvailable = hasCurrentPriorityComparisonData(previousCurrentPriority);
        int currentPriorityMissingAllStandardCodes =
                parseInt(currentPriority.value("active_baseline_user_profile_standard_code_users_missing_all_standard_codes"));
        int previousCurrentPriorityMissingAllStandardCodes =
                parseInt(previousCurrentPriority.value("active_baseline_user_profile_standard_code_users_missing_all_standard_codes"));
        int currentPriorityMissingAllStandardCodesDelta =
                currentPriorityMissingAllStandardCodes - previousCurrentPriorityMissingAllStandardCodes;
        String currentPriorityRecommendationObservationStatus =
                currentPriority.value("recommendation_standard_code_observation_status");
        String previousCurrentPriorityRecommendationObservationStatus =
                previousCurrentPriority.value("recommendation_standard_code_observation_status");
        boolean currentPriorityRecommendationObservationStatusChanged =
                !currentPriorityRecommendationObservationStatus.equals(previousCurrentPriorityRecommendationObservationStatus);
        String currentPriorityMissingAllStandardCodesDeltaLabel = buildMissingDeltaLabel(
                currentPriorityPreviousAvailable,
                currentPriorityMissingAllStandardCodesDelta
        );
        String currentPriorityRecommendationObservationStatusTransitionLabel = buildObservationTransitionLabel(
                currentPriorityPreviousAvailable,
                currentPriorityRecommendationObservationStatusChanged,
                previousCurrentPriorityRecommendationObservationStatus,
                currentPriorityRecommendationObservationStatus
        );

        return new AdminWrapperObservationResponse(
                activeBaseline.available(),
                activeBaseline.generatedAt(),
                activeBaseline.absolutePath(),
                activeBaseline.value("active_baseline_suite"),
                activeBaseline.value("ops_observation_status"),
                activeBaseline.value("ops_attention_feed_status"),
                parseInt(activeBaseline.value("ops_attention_feed_item_count")),
                parseInt(activeBaseline.value("ops_attention_feed_warning_item_count")),
                activeBaseline.value("ops_attention_feed_item_keys"),
                activeBaseline.value("ops_attention_feed_item_titles"),
                parseInt(activeBaseline.value("ops_user_profile_standard_code_users_with_any_standard_code")),
                parseInt(activeBaseline.value("ops_user_profile_standard_code_users_missing_all_standard_codes")),
                activeBaseline.value("ops_observation_status"),
                parseInt(activeBaseline.value("ops_recommendation_standard_code_housing_positive_rule_delta_rows")),
                parseInt(activeBaseline.value("ops_recommendation_standard_code_welfare_positive_rule_scenarios")),
                parseDouble(activeBaseline.value("ops_recommendation_standard_code_welfare_max_rule_delta")),
                currentPriority.available(),
                currentPriority.generatedAt(),
                currentPriority.absolutePath(),
                currentPriority.value("current_priority_suite"),
                Boolean.parseBoolean(currentPriority.value("active_baseline_reused")),
                currentPriority.value("active_baseline_attention_feed_status"),
                parseInt(currentPriority.value("active_baseline_attention_feed_item_count")),
                parseInt(currentPriority.value("active_baseline_attention_feed_warning_item_count")),
                currentPriority.value("active_baseline_attention_feed_item_keys"),
                currentPriority.value("active_baseline_attention_feed_item_titles"),
                parseInt(currentPriority.value("active_baseline_user_profile_standard_code_users_with_any_standard_code")),
                currentPriorityMissingAllStandardCodes,
                currentPriorityRecommendationObservationStatus,
                parseInt(currentPriority.value("recommendation_standard_code_housing_positive_rule_delta_rows")),
                parseInt(currentPriority.value("recommendation_standard_code_welfare_scenario_count")),
                parseInt(currentPriority.value("recommendation_standard_code_welfare_positive_rule_scenarios")),
                parseDouble(currentPriority.value("recommendation_standard_code_welfare_max_rule_delta")),
                currentPriorityPreviousAvailable,
                previousCurrentPriority.generatedAt(),
                previousCurrentPriority.absolutePath(),
                previousCurrentPriorityMissingAllStandardCodes,
                currentPriorityMissingAllStandardCodesDelta,
                currentPriorityMissingAllStandardCodesDeltaLabel,
                previousCurrentPriorityRecommendationObservationStatus,
                currentPriorityRecommendationObservationStatusChanged,
                currentPriorityRecommendationObservationStatusTransitionLabel,
                buildPromotedAlert(
                        currentPriorityPreviousAvailable,
                        currentPriorityMissingAllStandardCodesDelta,
                        currentPriorityMissingAllStandardCodesDeltaLabel,
                        currentPriorityRecommendationObservationStatus,
                        currentPriorityRecommendationObservationStatusChanged,
                        currentPriorityRecommendationObservationStatusTransitionLabel
                )
        );
    }

    private boolean hasCurrentPriorityComparisonData(SummarySnapshot snapshot) {
        if (!snapshot.available()) {
            return false;
        }
        String missingAllStandardCodes = snapshot.value("active_baseline_user_profile_standard_code_users_missing_all_standard_codes");
        String recommendationObservationStatus = snapshot.value("recommendation_standard_code_observation_status");
        return (missingAllStandardCodes != null && !missingAllStandardCodes.isBlank())
                || (recommendationObservationStatus != null && !recommendationObservationStatus.isBlank());
    }

    private String buildMissingDeltaLabel(boolean previousAvailable, int delta) {
        if (!previousAvailable) {
            return "이전값 없음";
        }
        if (delta > 0) {
            return delta + " 증가";
        }
        if (delta < 0) {
            return Math.abs(delta) + " 감소";
        }
        return "변화 없음";
    }

    private String buildObservationTransitionLabel(
            boolean previousAvailable,
            boolean changed,
            String previousStatus,
            String currentStatus
    ) {
        if (!previousAvailable) {
            return "이전값 없음";
        }
        if (!changed) {
            return "변화 없음";
        }
        return (previousStatus == null || previousStatus.isBlank() ? "—" : previousStatus)
                + " -> "
                + (currentStatus == null || currentStatus.isBlank() ? "—" : currentStatus);
    }

    private AdminWrapperObservationResponse.SnapshotAlert buildPromotedAlert(
            boolean previousAvailable,
            int missingDelta,
            String missingDeltaLabel,
            String currentObservationStatus,
            boolean observationStatusChanged,
            String observationTransitionLabel
    ) {
        if (!previousAvailable) {
            return null;
        }
        boolean observationWorsened = observationStatusChanged && !"passed".equals(currentObservationStatus);
        boolean observationImproved = observationStatusChanged && "passed".equals(currentObservationStatus);
        String message = "표준코드 미입력 " + missingDeltaLabel + ", priority 관측 " + observationTransitionLabel;
        if (missingDelta > 0 || observationWorsened) {
            return new AdminWrapperObservationResponse.SnapshotAlert(
                    "warning",
                    "운영 주시 포인트",
                    message
            );
        }
        if (missingDelta < 0 || observationImproved) {
            return new AdminWrapperObservationResponse.SnapshotAlert(
                    "success",
                    "개선 신호",
                    message
            );
        }
        return new AdminWrapperObservationResponse.SnapshotAlert(
                "info",
                "변화 없음",
                message
        );
    }

    private SummarySnapshot readSummary(Path summaryPath) {
        if (summaryPath == null || !Files.exists(summaryPath)) {
            return SummarySnapshot.unavailable(summaryPath);
        }

        try {
            List<String> lines = Files.readAllLines(summaryPath);
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : lines) {
                if (line == null || line.isBlank() || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                values.put(parts[0].trim(), parts[1].trim());
            }
            return SummarySnapshot.available(summaryPath, values, resolveGeneratedAt(summaryPath));
        } catch (IOException e) {
            return SummarySnapshot.unavailable(summaryPath);
        }
    }

    private LocalDateTime resolveGeneratedAt(Path summaryPath) {
        try {
            return LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(summaryPath).toInstant(),
                    ZoneId.systemDefault()
            );
        } catch (IOException e) {
            return null;
        }
    }

    private SummarySnapshot readPreviousSummary(Path latestSummaryPath, String summaryFileName) {
        if (latestSummaryPath == null) {
            return SummarySnapshot.unavailable(null);
        }
        Path rootDir = latestSummaryPath.getParent();
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return SummarySnapshot.unavailable(latestSummaryPath);
        }
        String latestRunDirName = findLatestRunDirectoryName(rootDir, summaryFileName);
        try (Stream<Path> entries = Files.list(rootDir)) {
            List<Path> runDirs = entries
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals("latest"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            Path previousRunDir = null;
            for (Path runDir : runDirs) {
                String runDirName = runDir.getFileName().toString();
                if (latestRunDirName != null && runDirName.equals(latestRunDirName)) {
                    break;
                }
                previousRunDir = runDir;
            }
            if (previousRunDir == null) {
                return SummarySnapshot.unavailable(rootDir.resolve(summaryFileName));
            }
            return readSummary(previousRunDir.resolve(summaryFileName));
        } catch (IOException e) {
            return SummarySnapshot.unavailable(rootDir.resolve(summaryFileName));
        }
    }

    private String findLatestRunDirectoryName(Path rootDir, String summaryFileName) {
        Path latestLink = rootDir.resolve("latest");
        if (Files.isDirectory(latestLink)) {
            Path candidate = latestLink.resolve(summaryFileName);
            if (Files.exists(candidate)) {
                Path relative = rootDir.relativize(candidate);
                if (relative.getNameCount() >= 2) {
                    return relative.getName(0).toString();
                }
            }
        }
        try (Stream<Path> entries = Files.list(rootDir)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals("latest"))
                    .filter(path -> Files.exists(path.resolve(summaryFileName)))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .reduce((first, second) -> second)
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    private record SummarySnapshot(
            boolean available,
            String absolutePath,
            Map<String, String> values,
            LocalDateTime generatedAt
    ) {
        static SummarySnapshot available(Path path, Map<String, String> values, LocalDateTime generatedAt) {
            return new SummarySnapshot(true, path.toAbsolutePath().toString(), values, generatedAt);
        }

        static SummarySnapshot unavailable(Path path) {
            return new SummarySnapshot(false, path == null ? "" : path.toAbsolutePath().toString(), Map.of(), null);
        }

        String value(String key) {
            return values.getOrDefault(key, "");
        }
    }
}
