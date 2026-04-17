package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.collect.validation.TextConstraintExtractor;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 공공API 3종 DTO → WelfareService Entity 변환
 * - unified_category 매핑 포함
 * - Jsoup strip으로 HTML 태그 제거
 */
@Slf4j
@Component
public class WelfareServiceMapper {

    private static final String[] ONLINE_APPLY_KEYWORDS = {
            "온라인", "인터넷", "홈페이지", "웹", "모바일", "앱", "신청페이지", "누리집"
    };

    // ===== 온통청년 =====

    public WelfareService fromYouth(YouthApiDto.Item item) {
        // aplyYmd: "20260101 ~ 20261231" 형식에서 시작/종료일 파싱
        LocalDate applyStart = parseApplyStartFromRange(item.getAplyYmd());
        LocalDate applyEnd   = parseApplyEndFromRange(item.getAplyYmd());
        boolean onlineApply = inferOnlineApply(item.getAplyUrlAddr(), item.getPlcyAplyMthdCn(), item.getAplyYmd());

        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(RawFieldValidator.normalize(item.getPlcyNo()))
                .title(stripAndNormalize(item.getPlcyNm()))
                .description(stripAndNormalize(item.getPlcyExplnCn()))
                .supportContent(stripAndNormalize(item.getPlcySprtCn()))
                .categoryMain(RawFieldValidator.normalize(item.getLclsfNm()))
                .categorySub(RawFieldValidator.normalize(item.getMclsfNm()))
                .keyword(RawFieldValidator.normalize(item.getPlcyKywdNm()))
                .unifiedCategory(mapYouthCategory(item.getLclsfNm()))
                .hostOrg(RawFieldValidator.normalize(item.getSprvsnInstCdNm()))
                .operatingOrg(RawFieldValidator.normalize(item.getOperInstCdNm()))
                .minAge(item.getSprtTrgtMinAge())
                .maxAge(item.getSprtTrgtMaxAge())
                .minIncome(item.getEarnMinAmt())
                .maxIncome(item.getEarnMaxAmt())
                .startDate(parseDate(item.getBizPrdBgngYmd()))
                .endDate(parseDate(item.getBizPrdEndYmd()))
                .applyStartDate(applyStart)
                .applyEndDate(applyEnd)
                .applyMethodName(RawFieldValidator.normalize(item.getPlcyAplyMthdCn()))
                .isOnlineApply(onlineApply)
                .detailUrl(RawFieldValidator.normalize(item.getAplyUrlAddr()))
                .apiViewCount(item.getInqCnt())
                .registeredAt(parseDateTimeLoose(item.getFrstRegDt()))
                .lastModifiedAt(parseDateTimeLoose(item.getLastMdfcnDt()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public List<ServiceTag> tagsFromYouth(YouthApiDto.Item item, WelfareService service) {
        List<ServiceTag> tags = new ArrayList<>();
        addTagsFromCsv(tags, service, item.getPlcyKywdNm(), ServiceTag.TagType.KEYWORD);
        addConstraintKeywordTags(tags, service,
                item.getPlcySprtCn(), item.getPlcyExplnCn(), item.getPlcyAplyMthdCn());
        return tags;
    }

    public List<ServiceRegion> regionsFromYouth(YouthApiDto.Item item, WelfareService service) {
        List<ServiceRegion> regions = new ArrayList<>();
        String regionCd = item.getZipCd();
        if (regionCd == null || regionCd.isBlank()) return regions;
        for (String code : regionCd.split(",")) {
            String c = code.strip();
            if (!c.isEmpty()) {
                regions.add(ServiceRegion.builder()
                        .service(service)
                        .regionCode(c)
                        .build());
            }
        }
        return regions;
    }

    // ===== 복지로 중앙 =====

    public WelfareService fromBokjiroCentral(BokjiroCentralDto.Item item) {
        TextConstraintExtractor.ConstraintSummary constraints = TextConstraintExtractor.summarize(
                item.getServDgst(),
                item.getTrgterIndvdlArray(),
                item.getLifeArray()
        );
        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId(RawFieldValidator.normalize(item.getServId()))
                .title(stripAndNormalize(item.getServNm()))
                .description(stripAndNormalize(item.getServDgst()))
                .supportContent(firstNonBlank(
                        stripAndNormalize(item.getServDgst()),
                        RawFieldValidator.normalize(item.getSrvPvsnNm())
                ))
                .unifiedCategory(mapBokjiroCategory(item.getIntrsThemaArray()))
                .hostOrg(RawFieldValidator.normalize(item.getJurMnofNm()))
                .operatingOrg(RawFieldValidator.normalize(item.getJurOrgNm()))
                .minAge(constraints.minAge())
                .maxAge(constraints.maxAge())
                .applyEndDate(constraints.applyEndDate())
                .lifeStage(RawFieldValidator.normalize(item.getLifeArray()))
                .supportCycle(RawFieldValidator.normalize(item.getSprtCycNm()))
                .provisionType(RawFieldValidator.normalize(item.getSrvPvsnNm()))
                .isOnlineApply("Y".equalsIgnoreCase(item.getOnapPsbltYn()))
                .detailUrl(RawFieldValidator.normalize(item.getServDtlLink()))
                .apiViewCount(item.getInqNum())
                .registeredAt(parseDateTimeLoose(item.getSvcfrstRegTs()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public List<ServiceTag> tagsFromBokjiroCentral(BokjiroCentralDto.Item item, WelfareService service) {
        List<ServiceTag> tags = new ArrayList<>();
        addTagsFromCsv(tags, service, item.getLifeArray(), ServiceTag.TagType.LIFE_STAGE);
        addTagsFromCsv(tags, service, item.getIntrsThemaArray(), ServiceTag.TagType.INTEREST_THEME);
        addTagsFromCsv(tags, service, item.getTrgterIndvdlArray(), ServiceTag.TagType.TARGET_GROUP);
        addConstraintKeywordTags(tags, service, item.getServDgst());
        return tags;
    }

    // 복지로 중앙은 전국 단위 → service_regions 미삽입
    public List<ServiceRegion> regionsFromBokjiroCentral(BokjiroCentralDto.Item item, WelfareService service) {
        return List.of();
    }

    // ===== 복지로 지자체 =====

    public WelfareService fromBokjiroLocal(BokjiroLocalDto.Item item) {
        TextConstraintExtractor.ConstraintSummary constraints = TextConstraintExtractor.summarize(
                item.getServDgst(),
                item.getTrgterIndvdlNmArray(),
                item.getAplyMtdNm()
        );
        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId(RawFieldValidator.normalize(item.getServId()))
                .title(stripAndNormalize(item.getServNm()))
                .description(stripAndNormalize(item.getServDgst()))
                .supportContent(firstNonBlank(
                        stripAndNormalize(item.getServDgst()),
                        RawFieldValidator.normalize(item.getSrvPvsnNm())
                ))
                .unifiedCategory(mapBokjiroCategory(item.getIntrsThemaNmArray()))
                .operatingOrg(RawFieldValidator.normalize(item.getBizChrDeptNm()))
                .minAge(constraints.minAge())
                .maxAge(constraints.maxAge())
                .applyEndDate(constraints.applyEndDate())
                .lifeStage(RawFieldValidator.normalize(item.getLifeNmArray()))
                .supportCycle(RawFieldValidator.normalize(item.getSprtCycNm()))
                .provisionType(RawFieldValidator.normalize(item.getSrvPvsnNm()))
                .applyMethodName(RawFieldValidator.normalize(item.getAplyMtdNm()))
                .isOnlineApply(inferOnlineApply(item.getServDtlLink(), item.getAplyMtdNm()))
                .detailUrl(RawFieldValidator.normalize(item.getServDtlLink()))
                .apiViewCount(item.getInqNum())
                .startDate(parseDate(item.getEnfcBgngYmd()))
                .endDate(parseDate(item.getEnfcEndYmd()))
                .lastModifiedAt(parseDateTimeLoose(item.getLastModYmd()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public List<ServiceTag> tagsFromBokjiroLocal(BokjiroLocalDto.Item item, WelfareService service) {
        List<ServiceTag> tags = new ArrayList<>();
        addTagsFromCsv(tags, service, item.getLifeNmArray(), ServiceTag.TagType.LIFE_STAGE);
        addTagsFromCsv(tags, service, item.getIntrsThemaNmArray(), ServiceTag.TagType.INTEREST_THEME);
        addTagsFromCsv(tags, service, item.getTrgterIndvdlNmArray(), ServiceTag.TagType.TARGET_GROUP);
        addConstraintKeywordTags(tags, service, item.getServDgst());
        return tags;
    }

    public List<ServiceRegion> regionsFromBokjiroLocal(BokjiroLocalDto.Item item, WelfareService service) {
        if (item.getCtpvNm() == null) {
            return List.of();
        }
        return List.of(ServiceRegion.builder()
                .service(service)
                .sidoName(item.getCtpvNm())
                .sggName(item.getSggNm())
                .build());
    }

    // ===== 공통 유틸 =====

    /** Jsoup으로 HTML 태그 제거 후 RawFieldValidator로 null/blank 정규화 */
    public String strip(String html) {
        if (html == null) return null;
        String cleaned = Jsoup.clean(html, Safelist.none()).strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Jsoup strip + RawFieldValidator.normalize 연결.
     * 모든 fromXxx 메서드에서 문자열 필드는 이 메서드를 사용한다.
     */
    private String stripAndNormalize(String html) {
        return RawFieldValidator.normalize(strip(html));
    }

    private void addTagsFromCsv(List<ServiceTag> tags, WelfareService service,
                                  String csv, ServiceTag.TagType type) {
        if (csv == null || csv.isBlank()) return;
        // 콤마 구분 + 앞뒤 유니코드 공백 제거 (탭, NBSP 등 포함)
        for (String v : csv.split(",")) {
            String value = v.strip();
            if (!value.isEmpty()) {
                tags.add(buildTag(service, type, value));
            }
        }
    }

    private ServiceTag buildTag(WelfareService service, ServiceTag.TagType type, String value) {
        return ServiceTag.builder()
                .service(service)
                .tagType(type)
                .tagValue(value)
                .build();
    }

    /**
     * 비정형 안내 문구의 자격/제한 조건을 규칙 기반으로 추출하여 KEYWORD 태그로 저장한다.
     * 예: COND_AGE_MAX_34, COND_INCOME_PCT_LE_130, COND_RENT_WON_LE_80
     */
    private void addConstraintKeywordTags(List<ServiceTag> tags, WelfareService service, String... texts) {
        Set<String> extracted = TextConstraintExtractor.extract(texts);
        extracted.forEach(token -> tags.add(buildTag(service, ServiceTag.TagType.KEYWORD, token)));
    }

    /**
     * unified_category 매핑 — 온통청년 lclsfNm(정책대분류) 기준
     * 실제 API 확인 값: 일자리 / 주거 / 교육지원 / 복지문화 / 참여·기반
     * (구 값도 방어적으로 처리)
     */
    private String mapYouthCategory(String lclsfNm) {
        if (lclsfNm == null) return "기타";
        return switch (lclsfNm.strip()) {
            case "일자리"               -> "일자리";
            case "주거"                 -> "주거";
            case "교육", "교육지원", "교육·직업훈련" -> "교육·직업훈련";
            case "복지문화", "금융·복지·문화" -> "금융·생활지원";
            case "참여권리", "참여·기반" -> "참여·기회";
            default                     -> "기타";
        };
    }

    /** unified_category 매핑 — 복지로 intrsThema 기준 (콤마 구분, 첫 번째 값 사용) */
    private String mapBokjiroCategory(String intrsThemaArray) {
        if (intrsThemaArray == null) return "기타";
        String first = intrsThemaArray.split(",")[0].trim();
        return switch (first) {
            case "일자리" -> "일자리";
            case "주거" -> "주거";
            case "교육" -> "교육·직업훈련";
            case "민간금융", "생활지원" -> "금융·생활지원";
            case "문화·여가" -> "문화·여가";
            case "신체건강", "정신건강" -> "건강·의료";
            case "보육", "보호·돌봄", "임신·출산" -> "가족·돌봄";
            case "안전·위기" -> "안전·위기";
            default -> "기타";
        };
    }

    /**
     * aplyYmd 형식: "20260101 ~ 20261231", "상시모집", null 등.
     * "yyyyMMdd ~ yyyyMMdd" 패턴에서 시작일 파싱. 그 외 null.
     */
    private LocalDate parseApplyStartFromRange(String aplyYmd) {
        if (aplyYmd == null) return null;
        String[] parts = aplyYmd.split("~");
        return parts.length >= 1 ? parseDate(parts[0].strip()) : null;
    }

    /** aplyYmd에서 종료일 파싱 */
    private LocalDate parseApplyEndFromRange(String aplyYmd) {
        if (aplyYmd == null) return null;
        String[] parts = aplyYmd.split("~");
        return parts.length >= 2 ? parseDate(parts[1].strip()) : null;
    }

    /**
     * "20240101", "2024-01-01", "2024/01/01" 형식을 파싱한다.
     * isDateSane 검사를 통과하지 못하면 null을 반환한다.
     */
    private LocalDate parseDate(String s) {
        if (!RawFieldValidator.isDateSane(s)) return null;
        try {
            String digits = s.replaceAll("[^0-9]", "");
            return LocalDate.parse(digits.substring(0, 8),
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException e) {
            log.debug("[Mapper] parseDate 실패: '{}'", s);
            return null;
        }
    }

    /**
     * 날짜(8자리) 또는 일시(14자리 이상) 문자열을 파싱한다.
     * isDateSane 검사를 통과하지 못하면 null을 반환한다.
     */
    private LocalDateTime parseDateTimeLoose(String s) {
        if (!RawFieldValidator.isDateSane(s)) return null;
        try {
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.length() >= 14) {
                return LocalDateTime.parse(digits.substring(0, 14),
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            }
            return LocalDate.parse(digits.substring(0, 8),
                    DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
        } catch (DateTimeParseException e) {
            log.debug("[Mapper] parseDateTimeLoose 실패: '{}'", s);
            return null;
        }
    }

    private boolean inferOnlineApply(String detailUrl, String... texts) {
        if (RawFieldValidator.normalize(detailUrl) != null) {
            return true;
        }
        if (texts == null) {
            return false;
        }
        for (String text : texts) {
            String normalized = RawFieldValidator.normalize(text);
            if (normalized == null) {
                continue;
            }
            for (String keyword : ONLINE_APPLY_KEYWORDS) {
                if (normalized.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = RawFieldValidator.normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
}
