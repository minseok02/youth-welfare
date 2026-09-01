package com.example.welfare.policy.support;

import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.WelfareService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PolicyRegionLabelSupport {

    private static final int BROAD_REGION_LABEL_MIN_COUNT = 8;

    private static final Map<String, String> REGION_ALIASES = Map.ofEntries(
            Map.entry("서울시", "서울특별시"),
            Map.entry("부산시", "부산광역시"),
            Map.entry("대구시", "대구광역시"),
            Map.entry("인천시", "인천광역시"),
            Map.entry("광주시", "광주광역시"),
            Map.entry("대전시", "대전광역시"),
            Map.entry("울산시", "울산광역시"),
            Map.entry("세종시", "세종특별자치시"),
            Map.entry("제주도", "제주특별자치도"),
            Map.entry("강원도", "강원특별자치도"),
            Map.entry("전라북도", "전북특별자치도")
    );

    private PolicyRegionLabelSupport() {
    }

    public static String resolvePreferredRegionLabel(WelfareService service, List<String> rawCandidates) {
        List<RegionCandidate> candidates = rawCandidates == null ? List.of() : rawCandidates.stream()
                .map(candidate -> new RegionCandidate(candidate, null))
                .toList();
        return resolvePreferredRegionLabelFromCandidates(service, candidates);
    }

    public static String resolvePreferredRegionLabelFromCandidates(WelfareService service, List<RegionCandidate> rawCandidates) {
        List<String> candidates = normalizeDistinct(rawCandidates);
        String organizationHint = firstNonNull(
                extractRegionHint(service.getOperatingOrg()),
                extractRegionHint(service.getHostOrg())
        );
        if (organizationHint != null) {
            String collapsedLabel = resolveBroadTopLevelLabel(candidates, organizationHint);
            if (collapsedLabel != null) {
                return collapsedLabel;
            }
            return chooseMatchingCandidateOrHint(organizationHint, candidates);
        }
        if (service.getSourceType() == WelfareService.SourceType.GOV24 && looksLikeCentralGovernmentAgency(service)) {
            // 중앙부처/기관 Gov24 정책은 stray region row를 local label로 노출하는 것이 더 위험하다.
            return null;
        }
        String collapsedLabel = resolveBroadTopLevelLabel(candidates, null);
        if (collapsedLabel != null) {
            return collapsedLabel;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public static List<String> extractRegionLabels(List<ServiceRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            return List.of();
        }
        List<RegionCandidate> candidates = regions.stream()
                .map(region -> new RegionCandidate(
                        joinNames(region.getSidoName(), region.getSggName()),
                        region.getRegionCode()
                ))
                .toList();
        return normalizeDistinct(candidates);
    }

    private static String chooseMatchingCandidateOrHint(String organizationHint, List<String> candidates) {
        String normalizedHint = normalizeRegionAlias(organizationHint);
        String bestMatch = null;
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeRegionAlias(candidate);
            if (normalizedCandidate.equals(normalizedHint)
                    || normalizedCandidate.startsWith(normalizedHint + " ")
                    || normalizedHint.startsWith(normalizedCandidate + " ")) {
                if (bestMatch == null || normalizedCandidate.length() > normalizeRegionAlias(bestMatch).length()) {
                    bestMatch = candidate;
                }
            }
        }
        return bestMatch != null ? bestMatch : normalizedHint;
    }

    private static String resolveBroadTopLevelLabel(List<String> candidates, String organizationHint) {
        if (candidates == null || candidates.size() < BROAD_REGION_LABEL_MIN_COUNT) {
            return null;
        }

        String normalizedHint = normalizeRegionAlias(organizationHint);
        String topLevelHint = extractTopLevelRegion(normalizedHint);
        if (normalizedHint != null && !normalizedHint.equals(topLevelHint)) {
            return null;
        }

        Set<String> topLevelRegions = new LinkedHashSet<>();
        for (String candidate : candidates) {
            String topLevelRegion = extractTopLevelRegion(normalizeRegionAlias(candidate));
            if (topLevelRegion != null) {
                topLevelRegions.add(topLevelRegion);
            }
        }
        if (topLevelRegions.size() != 1) {
            return null;
        }

        String onlyTopLevelRegion = topLevelRegions.iterator().next();
        if (topLevelHint != null && !topLevelHint.equals(onlyTopLevelRegion)) {
            return null;
        }
        return onlyTopLevelRegion;
    }

    private static String extractTopLevelRegion(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return null;
        }
        String firstToken = normalized.split("\\s+")[0];
        return looksLikeTopLevelRegion(firstToken) ? firstToken : null;
    }

    private static String extractRegionHint(String organizationName) {
        String normalized = normalizeBlank(organizationName);
        if (normalized == null) {
            return null;
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 0) {
            return null;
        }
        String first = normalizeRegionAlias(parts[0]);
        if (!looksLikeTopLevelRegion(first)) {
            return null;
        }
        if (parts.length > 1 && looksLikeLocalRegion(parts[1])) {
            return first + " " + parts[1];
        }
        return first;
    }

    private static boolean looksLikeTopLevelRegion(String token) {
        return token.endsWith("특별시")
                || token.endsWith("광역시")
                || token.endsWith("특별자치시")
                || token.endsWith("특별자치도")
                || token.endsWith("도");
    }

    private static boolean looksLikeLocalRegion(String token) {
        return token.endsWith("시") || token.endsWith("군") || token.endsWith("구");
    }

    private static List<String> normalizeDistinct(List<RegionCandidate> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (RegionCandidate value : values) {
            String normalized = normalizeCandidateLabel(value);
            if (normalized != null) {
                distinct.add(normalized);
            }
        }
        return new ArrayList<>(distinct);
    }

    private static String normalizeRegionAlias(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return null;
        }
        return REGION_ALIASES.getOrDefault(normalized, normalized);
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeCandidateLabel(RegionCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        String explicit = normalizeBlank(candidate.label());
        if (explicit != null) {
            return explicit;
        }
        RegionCodeUtil.RegionName regionName = RegionCodeUtil.getRegionName(candidate.regionCode());
        if (regionName == null) {
            return null;
        }
        return joinNames(regionName.sidoName(), regionName.sggName());
    }

    private static String joinNames(String sidoName, String sggName) {
        String sido = normalizeBlank(sidoName);
        String sgg = normalizeBlank(sggName);
        if (sido == null) {
            return null;
        }
        return sgg == null ? sido : sido + " " + sgg;
    }

    private static boolean looksLikeCentralGovernmentAgency(WelfareService service) {
        return looksLikeCentralGovernmentAgency(service.getHostOrg())
                || looksLikeCentralGovernmentAgency(service.getOperatingOrg());
    }

    private static boolean looksLikeCentralGovernmentAgency(String orgName) {
        String normalized = normalizeBlank(orgName);
        if (normalized == null) {
            return false;
        }
        if (normalized.endsWith("부") || normalized.endsWith("처") || normalized.endsWith("위원회")) {
            return true;
        }
        if (normalized.endsWith("청")) {
            return !(normalized.endsWith("시청")
                    || normalized.endsWith("도청")
                    || normalized.endsWith("군청")
                    || normalized.endsWith("구청")
                    || normalized.endsWith("교육청"));
        }
        return false;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public record RegionCandidate(String label, String regionCode) {
    }
}
