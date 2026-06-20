package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatActionLinkResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ChatApplicationActionLinkFactory {

    private static final int MAX_LINK_COUNT = 5;
    private static final Pattern URL_PREFIX_PATTERN = Pattern.compile(
            "(?i)^(https?://|www\\.)\\S+"
    );
    private static final Pattern TRAILING_URL_NOISE_PATTERN = Pattern.compile("[).,;:&?]+$");
    private static final Pattern HOST_LABEL_PATTERN = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    private final ObjectMapper objectMapper;

    public List<ChatActionLinkResponse> createLinks(WelfareService service, WelfareServiceDetail detail) {
        List<ChatActionLinkResponse> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (ReferenceUrlPayload item : parseReferenceUrls(detail != null ? detail.getReferenceUrlsJson() : null)) {
            addLink(links, seen, normalizeUrl(item.url()), classifyReferenceType(item), labelFor(item));
            if (links.size() >= MAX_LINK_COUNT) {
                return List.copyOf(links);
            }
        }

        addLink(links, seen, normalizeUrl(service.getDetailUrl()), "RELATED_SITE", "관련 사이트");
        if (detail != null) {
            addLink(links, seen, normalizeUrl(detail.getHomepageUrl()), "RELATED_SITE", "참고 홈페이지");
        }

        return List.copyOf(links);
    }

    private void addLink(List<ChatActionLinkResponse> links,
                         Set<String> seen,
                         String normalizedUrl,
                         String type,
                         String label) {
        if (!StringUtils.hasText(normalizedUrl) || links.size() >= MAX_LINK_COUNT || !seen.add(normalizedUrl)) {
            return;
        }
        links.add(ChatActionLinkResponse.builder()
                .type(type)
                .label(label)
                .url(normalizedUrl)
                .description(descriptionFor(type))
                .build());
    }

    private List<ReferenceUrlPayload> parseReferenceUrls(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            List<ReferenceUrlPayload> parsed = objectMapper.readValue(
                    rawJson, new TypeReference<List<ReferenceUrlPayload>>() {
                    });
            return parsed != null ? parsed : List.of();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String classifyReferenceType(ReferenceUrlPayload item) {
        String type = normalize(item.type());
        String label = normalize(item.label());
        String sourceField = normalize(item.sourceField());
        if ("APPLY".equals(type)) {
            return "OFFICIAL_APPLY";
        }
        if (containsAny(label, "서류", "서식", "공고", "첨부", "제출")
                || containsAny(sourceField, "form", "file", "document", "notice")) {
            return "DOCUMENTS";
        }
        if ("DETAIL".equals(type)) {
            return "RELATED_SITE";
        }
        return "NOTICE";
    }

    private String labelFor(ReferenceUrlPayload item) {
        String classified = classifyReferenceType(item);
        if ("OFFICIAL_APPLY".equals(classified)) {
            return "공식 신청";
        }
        if ("DOCUMENTS".equals(classified)) {
            return "공고/서류 확인";
        }
        if ("NOTICE".equals(classified)) {
            return StringUtils.hasText(item.label()) ? item.label().trim() : "공고/참고 링크";
        }
        return StringUtils.hasText(item.label()) ? item.label().trim() : "관련 사이트";
    }

    private String descriptionFor(String type) {
        return switch (type) {
            case "OFFICIAL_APPLY" -> "신청은 공식 기관 페이지에서 진행하세요.";
            case "DOCUMENTS" -> "제출서류, 서식, 공고문을 확인할 때 사용하세요.";
            case "NOTICE" -> "공고나 참고 안내를 확인할 때 사용하세요.";
            default -> "정책 원문이나 운영기관 안내를 확인할 때 사용하세요.";
        };
    }

    private String normalizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        Matcher matcher = URL_PREFIX_PATTERN.matcher(trimmed);
        String matched = matcher.find() ? matcher.group() : trimmed;
        String candidate = matched.regionMatches(true, 0, "www.", 0, 4)
                ? "https://" + matched
                : matched;

        String current = candidate;
        while (StringUtils.hasText(current)) {
            String normalized = normalizeUrlCandidate(TRAILING_URL_NOISE_PATTERN.matcher(current).replaceFirst(""));
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
            current = dropLastCodePoint(current);
        }
        return null;
    }

    private String normalizeUrlCandidate(String candidate) {
        try {
            URL url = new URL(candidate);
            String scheme = url.getProtocol();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            String asciiHost = normalizeHost(url.getHost());
            if (!StringUtils.hasText(asciiHost) || !isExternalHost(asciiHost)) {
                return null;
            }
            URI uri = new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    url.getUserInfo(),
                    asciiHost,
                    url.getPort(),
                    url.getPath(),
                    url.getQuery(),
                    url.getRef());
            return uri.toASCIIString();
        } catch (IllegalArgumentException | MalformedURLException | URISyntaxException e) {
            return null;
        }
    }

    private String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return null;
        }
        String asciiHost = IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
        for (String label : asciiHost.split("\\.", -1)) {
            if (!HOST_LABEL_PATTERN.matcher(label).matches()) {
                return null;
            }
        }
        return asciiHost;
    }

    private String dropLastCodePoint(String value) {
        int endIndex = value.offsetByCodePoints(value.length(), -1);
        return value.substring(0, endIndex);
    }

    private boolean isExternalHost(String host) {
        return host.contains(".");
    }

    private boolean containsAny(String value, String... needles) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lowered = value.toLowerCase();
        for (String needle : needles) {
            if (lowered.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReferenceUrlPayload(
            String url,
            String type,
            String label,
            String sourceField
    ) {
    }
}
