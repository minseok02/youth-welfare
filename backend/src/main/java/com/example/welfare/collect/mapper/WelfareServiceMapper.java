package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.support.BokjiroNormalizationSupport;
import com.example.welfare.collect.support.CollectCategorySupport;
import com.example.welfare.collect.support.YouthNormalizationSupport;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.collect.validation.TextConstraintExtractor;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        String detailUrl = firstNormalizedUrl(item.getAplyUrlAddr(), item.getRefUrlAddr1(), item.getRefUrlAddr2());
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
                .unifiedCategory(CollectCategorySupport.mapYouthCompatCategory(item.getLclsfNm()))
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
                .detailUrl(detailUrl)
                .apiViewCount(item.getInqCnt())
                .registeredAt(parseDateTimeLoose(item.getFrstRegDt()))
                .lastModifiedAt(parseDateTimeLoose(item.getLastMdfcnDt()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public NormalizedPolicyAggregate toNormalizedYouth(YouthApiDto.Item item) {
        WelfareService service = fromYouth(item);
        YouthNormalizationSupport.YouthMidPartition youthMidPartition =
                YouthNormalizationSupport.partitionYouthMidLabels(item.getMclsfNm());
        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .detail(buildDetail(service, null))
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .provisionMethod(service.getApplyMethodName())
                        .summaryLabels(YouthNormalizationSupport.summaryLabels(service, youthMidPartition))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(YouthNormalizationSupport.taxonomyTerms(item, youthMidPartition))
                .facts(YouthNormalizationSupport.facts(service))
                .build();
    }

    public List<ServiceTag> tagsFromYouth(YouthApiDto.Item item, WelfareService service) {
        List<ServiceTag> tags = new ArrayList<>();
        addTagsFromCsv(tags, service, item.getPlcyKywdNm(), ServiceTag.TagType.KEYWORD);
        addConstraintKeywordTags(tags, service,
                item.getPlcySprtCn(), item.getPlcyExplnCn(), item.getPlcyAplyMthdCn());
        return tags;
    }

    // 온통청년 API는 "전국 노출" 정책에 255개 시군구 코드를 모두 부여한다.
    // zipCd가 이 수 이상의 시도에 걸쳐 있으면 전국 마커로 판단하고, host_org 기반 지역 추정으로 전환한다.
    private static final int NATIONWIDE_SIDO_THRESHOLD = 15;

    // [지역 추정 한계]
    // host_org가 중앙부처(고용노동부 등)인 경우 inferFromHostOrg가 빈 리스트를 반환한다.
    // → service_regions에 행이 없으면 NOT EXISTS 조건으로 전체 지역 필터에 노출된다(전국 정책으로 처리).
    // 그러나 중앙부처가 주관하더라도 특정 지역 대상인 정책은 전국 노출로 잘못 처리될 수 있다.
    // 온통청년 API 자체에 명확한 지역 정보가 없으므로 현재로서는 이 방식이 최선이다.
    public List<ServiceRegion> regionsFromYouth(YouthApiDto.Item item, WelfareService service) {
        List<ServiceRegion> regions = new ArrayList<>();
        String regionCd = item.getZipCd();
        if (regionCd == null || regionCd.isBlank()) return regions;

        List<String> codes = new ArrayList<>();
        for (String code : regionCd.split(",")) {
            String c = code.strip();
            if (!c.isEmpty()) codes.add(c);
        }

        long distinctSido = codes.stream()
                .filter(c -> c.length() >= 2)
                .map(c -> c.substring(0, 2))
                .distinct()
                .count();

        // zipCd가 전국 수준이면 host_org로 실제 운영 지역 추정
        // 추정 불가(중앙부처 등)이면 빈 리스트 → 전국 정책으로 처리
        if (distinctSido >= NATIONWIDE_SIDO_THRESHOLD) {
            List<String> inferred = RegionCodeUtil.inferFromHostOrg(item.getSprvsnInstCdNm());
            for (String code : inferred) {
                regions.add(ServiceRegion.builder()
                        .service(service)
                        .regionCode(code)
                        .build());
            }
            return regions;
        }

        for (String code : codes) {
            regions.add(ServiceRegion.builder()
                    .service(service)
                    .regionCode(code)
                    .build());
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
                .unifiedCategory(CollectCategorySupport.mapBokjiroCompatCategory(item.getIntrsThemaArray()))
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

    public NormalizedPolicyAggregate toNormalizedBokjiroCentral(BokjiroCentralDto.Item item,
                                                                BokjiroDetailClient.DetailPayload detailPayload) {
        WelfareService service = fromBokjiroCentral(item);
        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .detail(buildDetail(service, detailPayload))
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .authority(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                        .confidence(BigDecimal.valueOf(0.85))
                        .build())
                .taxonomyTerms(BokjiroNormalizationSupport.centralTerms(item))
                .facts(BokjiroNormalizationSupport.derivedFacts(service, item.getServDgst()))
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
                .unifiedCategory(CollectCategorySupport.mapBokjiroCompatCategory(item.getIntrsThemaNmArray()))
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

    public NormalizedPolicyAggregate toNormalizedBokjiroLocal(BokjiroLocalDto.Item item,
                                                              BokjiroDetailClient.DetailPayload detailPayload) {
        WelfareService service = fromBokjiroLocal(item);
        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .detail(buildDetail(service, detailPayload))
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .authority(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                        .confidence(BigDecimal.valueOf(0.85))
                        .build())
                .taxonomyTerms(BokjiroNormalizationSupport.localTerms(item))
                .facts(BokjiroNormalizationSupport.derivedFacts(service, item.getServDgst()))
                .build();
    }

    public NormalizedPolicyAggregate toNormalizedBokjiroDetail(WelfareService service,
                                                               BokjiroDetailClient.DetailPayload detailPayload) {
        if (service == null || detailPayload == null) {
            throw new IllegalArgumentException("service/detailPayload 는 필수입니다.");
        }
        if (service.getSourceType() != WelfareService.SourceType.BOKJIRO_CENTRAL
                && service.getSourceType() != WelfareService.SourceType.BOKJIRO_LOCAL) {
            throw new IllegalArgumentException("복지로 상세 aggregate 는 복지로 source 에만 사용할 수 있습니다.");
        }

        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .detail(buildDetail(service, detailPayload))
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .authority(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                        .confidence(BigDecimal.valueOf(0.85))
                        .build())
                .taxonomyTerms(BokjiroNormalizationSupport.detailTerms(detailPayload))
                .facts(BokjiroNormalizationSupport.detailFacts(detailPayload))
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

    private String firstNormalizedUrl(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = RawFieldValidator.normalize(value);
            if (normalized == null) {
                continue;
            }
            if (normalized.regionMatches(true, 0, "www.", 0, 4)) {
                return "https://" + normalized;
            }
            return normalized;
        }
        return null;
    }

    private Map<String, String> summaryLabels(String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("summaryLabels 는 key/value 쌍이어야 합니다.");
        }

        java.util.LinkedHashMap<String, String> labels = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = RawFieldValidator.normalize(keyValues[i]);
            String value = RawFieldValidator.normalize(keyValues[i + 1]);
            if (key == null || value == null) {
                continue;
            }
            labels.put(key, value);
        }
        return Map.copyOf(labels);
    }

    private NormalizedPolicyAggregate.Core buildCore(WelfareService service) {
        return NormalizedPolicyAggregate.Core.builder()
                .sourceType(NormalizedPolicyAggregate.SourceType.valueOf(service.getSourceType().name()))
                .sourceId(service.getSourceId())
                .title(service.getTitle())
                .summary(firstNonBlank(service.getDescription(), service.getSupportContent()))
                .description(service.getDescription())
                .supportContent(service.getSupportContent())
                .hostOrg(service.getHostOrg())
                .operatingOrg(service.getOperatingOrg())
                .status(NormalizedPolicyAggregate.ServiceStatus.valueOf(service.getStatus().name()))
                .startDate(service.getStartDate())
                .endDate(service.getEndDate())
                .applyStartDate(service.getApplyStartDate())
                .applyEndDate(service.getApplyEndDate())
                .detailUrl(service.getDetailUrl())
                .onlineApply(service.getIsOnlineApply())
                .apiViewCount(service.getApiViewCount())
                .registeredAt(service.getRegisteredAt())
                .lastModifiedAt(service.getLastModifiedAt())
                .build();
    }

    private NormalizedPolicyAggregate.Detail buildDetail(WelfareService service,
                                                         BokjiroDetailClient.DetailPayload detailPayload) {
        return NormalizedPolicyAggregate.Detail.builder()
                .targetDetail(detailPayload == null ? null : RawFieldValidator.normalize(detailPayload.getTargetDetail()))
                .supportDetail(firstNonBlank(
                        detailPayload == null ? null : detailPayload.getSupportDetail(),
                        service.getSupportContent()
                ))
                .applyMethodDetail(firstNonBlank(
                        detailPayload == null ? null : detailPayload.getApplyMethodDetail(),
                        service.getApplyMethodName()
                ))
                .selectionCriteria(detailPayload == null ? null : RawFieldValidator.normalize(detailPayload.getSelectionCriteria()))
                .requiredDocuments(null)
                .contactText(detailPayload == null ? null : RawFieldValidator.normalize(detailPayload.getContactList()))
                .legalBasisText(null)
                .onlineApplyUrl(service.getIsOnlineApply() != null && service.getIsOnlineApply() ? service.getDetailUrl() : null)
                .supportCycle(firstNonBlank(
                        detailPayload == null ? null : detailPayload.getSupportCycle(),
                        service.getSupportCycle()
                ))
                .provisionType(firstNonBlank(
                        detailPayload == null ? null : detailPayload.getProvisionType(),
                        service.getProvisionType()
                ))
                .build();
    }

}
