package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공API 3종 DTO → WelfareService Entity 변환
 * - unified_category 매핑 포함
 * - Jsoup strip으로 HTML 태그 제거
 */
@Component
public class WelfareServiceMapper {

    // ===== 온통청년 =====

    public WelfareService fromYouth(YouthApiDto.Item item) {
        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(item.getBizId())
                .title(strip(item.getPolyBizSjnm()))
                .description(strip(item.getPolyItcnCn()))
                .supportContent(strip(item.getSporCn()))
                .categoryMain(item.getPolyBizTy())
                .categorySub(item.getPolyBizSecd())
                .keyword(item.getKeywords())
                .unifiedCategory(mapYouthCategory(item.getPolyBizTy()))
                .hostOrg(item.getMngtMson())
                .operatingOrg(item.getImplMson())
                .minAge(item.getMinAge())
                .maxAge(item.getMaxAge())
                .minIncome(item.getIncmeLowLimit())
                .maxIncome(item.getIncmeUpLimit())
                .startDate(parseDate(item.getBizPrdBgngDt()))
                .endDate(parseDate(item.getBizPrdEndDt()))
                .applyStartDate(parseDate(item.getRqutPrdBgngDt()))
                .applyEndDate(parseDate(item.getRqutPrdEndDt()))
                .applyMethodName(item.getAplyMthdItm())
                .detailUrl(item.getRqutUrla())
                .apiViewCount(item.getInqNum())
                .registeredAt(parseDateTimeLoose(item.getPlyBizInsDt()))
                .lastModifiedAt(parseDateTimeLoose(item.getPlyBizMdfcnDt()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public List<ServiceTag> tagsFromYouth(YouthApiDto.Item item, WelfareService service) {
        List<ServiceTag> tags = new ArrayList<>();
        if (item.getKeywords() != null) {
            for (String kw : item.getKeywords().split(",")) {
                String v = kw.trim();
                if (!v.isEmpty()) {
                    tags.add(buildTag(service, ServiceTag.TagType.KEYWORD, v));
                }
            }
        }
        return tags;
    }

    public List<ServiceRegion> regionsFromYouth(YouthApiDto.Item item, WelfareService service) {
        List<ServiceRegion> regions = new ArrayList<>();
        if (item.getRegionCd() != null) {
            for (String code : item.getRegionCd().split(",")) {
                String c = code.trim();
                if (!c.isEmpty()) {
                    regions.add(ServiceRegion.builder()
                            .service(service)
                            .regionCode(c)
                            .build());
                }
            }
        }
        return regions;
    }

    // ===== 복지로 중앙 =====

    public WelfareService fromBokjiroCentral(BokjiroCentralDto.Item item) {
        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId(item.getServId())
                .title(strip(item.getServNm()))
                .description(strip(item.getServDgst()))
                .unifiedCategory(mapBokjiroCategory(item.getIntrsThemaArray()))
                .hostOrg(item.getJurMnofNm())
                .operatingOrg(item.getJurOrgNm())
                .lifeStage(item.getLifeArray())
                .supportCycle(item.getSprtCycNm())
                .provisionType(item.getSrvPvsnNm())
                .isOnlineApply("Y".equalsIgnoreCase(item.getOnapPsbltYn()))
                .detailUrl(item.getServDtlLink())
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
        return tags;
    }

    // 복지로 중앙은 전국 단위 → service_regions 미삽입
    public List<ServiceRegion> regionsFromBokjiroCentral(BokjiroCentralDto.Item item, WelfareService service) {
        return List.of();
    }

    // ===== 복지로 지자체 =====

    public WelfareService fromBokjiroLocal(BokjiroLocalDto.Item item) {
        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId(item.getServId())
                .title(strip(item.getServNm()))
                .description(strip(item.getServDgst()))
                .unifiedCategory(mapBokjiroCategory(item.getIntrsThemaNmArray()))
                .operatingOrg(item.getBizChrDeptNm())
                .lifeStage(item.getLifeNmArray())
                .supportCycle(item.getSprtCycNm())
                .provisionType(item.getSrvPvsnNm())
                .applyMethodName(item.getAplyMtdNm())
                .detailUrl(item.getServDtlLink())
                .apiViewCount(item.getInqNum())
                .lastModifiedAt(parseDateTimeLoose(item.getLastModYmd()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public List<ServiceTag> tagsFromBokjiroLocal(BokjiroLocalDto.Item item, WelfareService service) {
        List<ServiceTag> tags = new ArrayList<>();
        addTagsFromCsv(tags, service, item.getLifeNmArray(), ServiceTag.TagType.LIFE_STAGE);
        addTagsFromCsv(tags, service, item.getIntrsThemaNmArray(), ServiceTag.TagType.INTEREST_THEME);
        addTagsFromCsv(tags, service, item.getTrgterIndvdlNmArray(), ServiceTag.TagType.TARGET_GROUP);
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

    /** Jsoup으로 HTML 태그 및 위험 속성 제거 */
    public String strip(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, Safelist.none()).trim();
    }

    private void addTagsFromCsv(List<ServiceTag> tags, WelfareService service,
                                  String csv, ServiceTag.TagType type) {
        if (csv == null) return;
        for (String v : csv.split(",")) {
            String value = v.trim();
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

    /** unified_category 매핑 — 온통청년 대분류 기준 */
    private String mapYouthCategory(String polyBizTy) {
        if (polyBizTy == null) return "기타";
        return switch (polyBizTy.trim()) {
            case "일자리" -> "일자리";
            case "주거" -> "주거";
            case "교육·직업훈련" -> "교육·직업훈련";
            case "금융·복지·문화" -> "금융·생활지원";
            case "참여·기회" -> "참여·기회";
            default -> "기타";
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

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.replace("-", "").replace("/", ""),
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTimeLoose(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String normalized = s.replaceAll("[^0-9]", "");
            if (normalized.length() >= 14) {
                return LocalDateTime.parse(normalized.substring(0, 14),
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            }
            if (normalized.length() >= 8) {
                return LocalDate.parse(normalized.substring(0, 8),
                        DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay();
            }
        } catch (DateTimeParseException e) {
            // 파싱 실패 시 null 반환
        }
        return null;
    }
}
