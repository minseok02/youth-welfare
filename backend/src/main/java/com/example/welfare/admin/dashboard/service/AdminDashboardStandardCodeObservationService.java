package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminStandardCodeEffectObservationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardStandardCodeObservationService {

    private static final Path DEFAULT_SUMMARY_PATH = Path.of(
            "tmp",
            "recommendation-observation",
            "latest-recommendation-observation-summary.txt"
    );

    public AdminStandardCodeEffectObservationResponse getLatestObservation() {
        return fromSummaryPath(DEFAULT_SUMMARY_PATH);
    }

    AdminStandardCodeEffectObservationResponse fromSummaryPath(Path summaryPath) {
        if (summaryPath == null || !Files.exists(summaryPath)) {
            return unavailable(summaryPath);
        }

        Map<String, String> values = parseSummary(summaryPath);
        LocalDateTime generatedAt = resolveGeneratedAt(summaryPath);
        return new AdminStandardCodeEffectObservationResponse(
                true,
                generatedAt,
                summaryPath.toAbsolutePath().toString(),
                values.getOrDefault("precheck_status", ""),
                values.getOrDefault("decision_class", ""),
                values.getOrDefault("housing_standard_code_effect_status", ""),
                parseInt(values.get("housing_standard_code_effect_positive_rule_delta_rows")),
                parseInt(values.get("housing_standard_code_effect_positive_final_delta_rows")),
                parseDouble(values.get("housing_standard_code_effect_max_rule_delta")),
                parseDouble(values.get("housing_standard_code_effect_max_final_delta")),
                values.getOrDefault("housing_standard_code_effect_top_positive_rule_delta_rows", ""),
                values.getOrDefault("welfare_standard_code_matrix_status", ""),
                parseInt(values.get("welfare_standard_code_matrix_scenario_count")),
                parseInt(values.get("welfare_standard_code_matrix_positive_rule_scenarios")),
                parseInt(values.get("welfare_standard_code_matrix_positive_final_scenarios")),
                values.getOrDefault("welfare_standard_code_matrix_max_rule_delta_scenario", ""),
                parseDouble(values.get("welfare_standard_code_matrix_max_rule_delta")),
                values.getOrDefault("welfare_standard_code_matrix_max_final_delta_scenario", ""),
                parseDouble(values.get("welfare_standard_code_matrix_max_final_delta")),
                values.getOrDefault("welfare_standard_code_matrix_scenario_rule_delta_snapshot", "")
        );
    }

    private AdminStandardCodeEffectObservationResponse unavailable(Path summaryPath) {
        return new AdminStandardCodeEffectObservationResponse(
                false,
                null,
                summaryPath == null ? "" : summaryPath.toAbsolutePath().toString(),
                "",
                "",
                "missing",
                0,
                0,
                0,
                0,
                "",
                "missing",
                0,
                0,
                0,
                "",
                0,
                "",
                0,
                ""
        );
    }

    private Map<String, String> parseSummary(Path summaryPath) {
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
            return values;
        } catch (IOException e) {
            return Map.of();
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
}
