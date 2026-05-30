package com.example.welfare.policy.support;

import java.util.List;
import java.util.Set;

public final class Gov24ServiceFieldSupport {

    private static final List<String> MANAGED_LABELS = List.of(
            "생활안정",
            "농림축산어업",
            "보육·교육",
            "보건·의료",
            "임신·출산",
            "고용·창업",
            "문화·환경",
            "보호·돌봄",
            "행정·안전",
            "주거·자립"
    );
    private static final Set<String> MANAGED_LABEL_SET = Set.copyOf(MANAGED_LABELS);

    private Gov24ServiceFieldSupport() {
    }

    public static List<String> managedLabels() {
        return MANAGED_LABELS;
    }

    public static String normalizeManagedLabel(String rawLabel) {
        if (rawLabel == null) {
            return null;
        }
        String trimmed = rawLabel.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return MANAGED_LABEL_SET.contains(trimmed) ? trimmed : null;
    }
}
