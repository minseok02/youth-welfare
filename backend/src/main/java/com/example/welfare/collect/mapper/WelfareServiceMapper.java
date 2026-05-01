package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.support.BokjiroNormalizationSupport;
import com.example.welfare.collect.support.CollectCategorySupport;
import com.example.welfare.collect.support.NormalizationKeySupport;
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
import java.util.LinkedHashSet;
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
                .detailUrl(RawFieldValidator.normalize(item.getAplyUrlAddr()))
                .apiViewCount(item.getInqCnt())
                .registeredAt(parseDateTimeLoose(item.getFrstRegDt()))
                .lastModifiedAt(parseDateTimeLoose(item.getLastMdfcnDt()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public NormalizedPolicyAggregate toNormalizedYouth(YouthApiDto.Item item) {
        WelfareService service = fromYouth(item);
        YouthMidPartition youthMidPartition = partitionYouthMidLabels(item.getMclsfNm());
        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .detail(buildDetail(service, null))
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .provisionMethod(service.getApplyMethodName())
                        .summaryLabels(summaryLabels(
                                NormalizationKeySupport.SUMMARY_KEY_YOUTH_MAJOR, service.getCategoryMain(),
                                NormalizationKeySupport.SUMMARY_KEY_YOUTH_MID, youthMidPartition.summaryLabel()
                        ))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(youthTerms(item, youthMidPartition))
                .facts(youthFacts(service))
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
                .taxonomyTerms(bokjiroCentralTerms(item))
                .facts(bokjiroDerivedFacts(service, item.getServDgst()))
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
                .taxonomyTerms(bokjiroLocalTerms(item))
                .facts(bokjiroDerivedFacts(service, item.getServDgst()))
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
                .taxonomyTerms(bokjiroDetailTerms(detailPayload))
                .facts(bokjiroDetailFacts(detailPayload))
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

    private List<NormalizedPolicyAggregate.TaxonomyTerm> youthTerms(YouthApiDto.Item item,
                                                                    YouthMidPartition youthMidPartition) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = new ArrayList<>();
        addTaxonomyTerm(terms,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MAJOR,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MAJOR,
                null,
                item.getLclsfNm(),
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_MAJOR,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        int sortOrder = 0;
        for (String label : youthMidPartition.officialLabels()) {
            addTaxonomyTerm(terms,
                    NormalizationKeySupport.TERM_GROUP_YOUTH_MID,
                    NormalizationKeySupport.TERM_GROUP_YOUTH_MID,
                    null,
                    label,
                    NormalizationKeySupport.SOURCE_FIELD_YOUTH_CATEGORY_SUB,
                    NormalizedPolicyAggregate.Authority.OFFICIAL, sortOrder++);
        }
        for (String label : youthMidPartition.rawAliases()) {
            addTaxonomyTerm(terms,
                    NormalizationKeySupport.TERM_GROUP_YOUTH_MID_RAW_ALIAS,
                    null,
                    null,
                    label,
                    NormalizationKeySupport.SOURCE_FIELD_YOUTH_CATEGORY_SUB,
                    NormalizedPolicyAggregate.Authority.OFFICIAL, sortOrder++);
        }
        addTaxonomyTermsFromCsv(terms,
                NormalizationKeySupport.TERM_GROUP_YOUTH_KEYWORD,
                NormalizationKeySupport.TERM_GROUP_YOUTH_KEYWORD,
                item.getPlcyKywdNm(),
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_KEYWORD,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        return terms;
    }

    private YouthMidPartition partitionYouthMidLabels(String rawYouthMid) {
        if (rawYouthMid == null || rawYouthMid.isBlank()) {
            return new YouthMidPartition(List.of(), List.of());
        }

        LinkedHashSet<String> officialLabels = new LinkedHashSet<>();
        LinkedHashSet<String> rawAliases = new LinkedHashSet<>();
        for (String rawToken : rawYouthMid.split(",")) {
            String label = RawFieldValidator.normalize(rawToken == null ? null : rawToken.strip());
            if (label == null) {
                continue;
            }
            if (NormalizationKeySupport.isOfficialYouthMidLabel(label)) {
                officialLabels.add(label);
            } else {
                rawAliases.add(label);
            }
        }
        return new YouthMidPartition(List.copyOf(officialLabels), List.copyOf(rawAliases));
    }

    private List<NormalizedPolicyAggregate.TaxonomyTerm> bokjiroCentralTerms(BokjiroCentralDto.Item item) {
        return bokjiroTerms(
                item.getLifeArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_LIFE_ARRAY,
                item.getIntrsThemaArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_INTEREST_THEME_ARRAY,
                item.getTrgterIndvdlArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_TARGET_GROUP_ARRAY
        );
    }

    private List<NormalizedPolicyAggregate.TaxonomyTerm> bokjiroLocalTerms(BokjiroLocalDto.Item item) {
        return bokjiroTerms(
                item.getLifeNmArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_LIFE_NM_ARRAY,
                item.getIntrsThemaNmArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_INTEREST_THEME_NM_ARRAY,
                item.getTrgterIndvdlNmArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_TARGET_GROUP_NM_ARRAY
        );
    }

    private List<NormalizedPolicyAggregate.TaxonomyTerm> bokjiroTerms(String lifeStageCsv,
                                                                      String lifeStageField,
                                                                      String interestThemeCsv,
                                                                      String interestThemeField,
                                                                      String targetGroupCsv,
                                                                      String targetGroupField) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = new ArrayList<>();
        addTaxonomyTermsFromCsv(terms, NormalizationKeySupport.TERM_GROUP_LIFE_STAGE, null, lifeStageCsv, lifeStageField,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        addTaxonomyTermsFromCsv(terms, NormalizationKeySupport.TERM_GROUP_INTEREST_THEME, null, interestThemeCsv, interestThemeField,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        addTaxonomyTermsFromCsv(terms, NormalizationKeySupport.TERM_GROUP_TARGET_GROUP, null, targetGroupCsv, targetGroupField,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        return terms;
    }

    private List<NormalizedPolicyAggregate.Fact> youthFacts(WelfareService service) {
        List<NormalizedPolicyAggregate.Fact> facts = new ArrayList<>();
        addRangeFact(facts,
                NormalizationKeySupport.FACT_GROUP_AGE,
                NormalizationKeySupport.FACT_CODE_YOUTH_AGE,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_AGE,
                "지원 연령", service.getMinAge(), service.getMaxAge(), "세",
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_AGE, NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        addBoundaryFact(facts,
                NormalizationKeySupport.FACT_GROUP_INCOME,
                NormalizationKeySupport.FACT_CODE_YOUTH_INCOME_MIN,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_INCOME_MIN,
                "소득 하한", service.getMinIncome(),
                NormalizedPolicyAggregate.Operator.GTE, "legacy-int", NormalizationKeySupport.SOURCE_FIELD_YOUTH_INCOME_MIN,
                NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        addBoundaryFact(facts,
                NormalizationKeySupport.FACT_GROUP_INCOME,
                NormalizationKeySupport.FACT_CODE_YOUTH_INCOME_MAX,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_INCOME_MAX,
                "소득 상한", service.getMaxIncome(),
                NormalizedPolicyAggregate.Operator.LTE, "legacy-int", NormalizationKeySupport.SOURCE_FIELD_YOUTH_INCOME_MAX,
                NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        addDateFact(facts,
                NormalizationKeySupport.FACT_GROUP_APPLY_END_DATE,
                NormalizationKeySupport.FACT_CODE_YOUTH_APPLY_END_DATE,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_APPLY_END_DATE,
                "신청 종료일", service.getApplyEndDate(),
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_APPLY_END_DATE, NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        return facts;
    }

    private List<NormalizedPolicyAggregate.Fact> bokjiroDerivedFacts(WelfareService service, String evidenceText) {
        List<NormalizedPolicyAggregate.Fact> facts = new ArrayList<>();
        addRangeFact(facts,
                NormalizationKeySupport.FACT_GROUP_AGE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_AGE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_AGE,
                "지원 연령", service.getMinAge(), service.getMaxAge(), "세",
                NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_DIGEST, NormalizedPolicyAggregate.Authority.RULE_DERIVED, BigDecimal.valueOf(0.90), evidenceText);
        addDateFact(facts,
                NormalizationKeySupport.FACT_GROUP_APPLY_END_DATE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_APPLY_END_DATE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_APPLY_END_DATE,
                "신청 종료일", service.getApplyEndDate(),
                NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_DIGEST, NormalizedPolicyAggregate.Authority.RULE_DERIVED, BigDecimal.valueOf(0.80), evidenceText);
        return facts;
    }

    private List<NormalizedPolicyAggregate.Fact> bokjiroDetailFacts(BokjiroDetailClient.DetailPayload detailPayload) {
        TextConstraintExtractor.ConstraintSummary constraints = TextConstraintExtractor.summarize(
                detailPayload.getTargetDetail(),
                detailPayload.getSelectionCriteria(),
                detailPayload.getApplyMethodDetail(),
                detailPayload.getSupportDetail()
        );

        String evidenceText = firstNonBlank(
                detailPayload.getTargetDetail(),
                detailPayload.getSelectionCriteria(),
                detailPayload.getApplyMethodDetail(),
                detailPayload.getSupportDetail()
        );

        List<NormalizedPolicyAggregate.Fact> facts = new ArrayList<>();
        addRangeFact(facts,
                NormalizationKeySupport.FACT_GROUP_AGE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_AGE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_AGE,
                "지원 연령", constraints.minAge(), constraints.maxAge(), "세",
                NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA, NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                BigDecimal.valueOf(0.90), evidenceText);
        addDateFact(facts,
                NormalizationKeySupport.FACT_GROUP_APPLY_END_DATE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_APPLY_END_DATE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_APPLY_END_DATE,
                "신청 종료일", constraints.applyEndDate(),
                NormalizationKeySupport.SOURCE_FIELD_APPLY_METHOD_DETAIL_SUPPORT_DETAIL, NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                BigDecimal.valueOf(0.80), evidenceText);
        return facts;
    }

    private List<NormalizedPolicyAggregate.TaxonomyTerm> bokjiroDetailTerms(BokjiroDetailClient.DetailPayload detailPayload) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = new ArrayList<>();
        int sortOrder = 0;
        for (String label : BokjiroNormalizationSupport.beneficiaryLabels(
                detailPayload.getTargetDetail(),
                detailPayload.getSelectionCriteria()
        )) {
            addTaxonomyTerm(
                    terms,
                    NormalizationKeySupport.TERM_GROUP_TARGET_GROUP,
                    null,
                    null,
                    label,
                    NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA,
                    NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED,
                    sortOrder++
            );
        }
        return terms;
    }

    private void addTaxonomyTermsFromCsv(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                         String termGroup,
                                         String codeSetKey,
                                         String csv,
                                         String sourceField,
                                         NormalizedPolicyAggregate.Authority authority,
                                         int startSortOrder) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        int sortOrder = startSortOrder;
        for (String raw : csv.split(",")) {
            String label = RawFieldValidator.normalize(raw == null ? null : raw.strip());
            if (label == null) {
                continue;
            }
            terms.add(NormalizedPolicyAggregate.TaxonomyTerm.builder()
                    .termGroup(termGroup)
                    .codeSetKey(codeSetKey)
                    .termCode(null)
                    .termLabel(label)
                    .sourceField(sourceField)
                    .authority(authority)
                    .sortOrder(sortOrder++)
                    .build());
        }
    }

    private void addTaxonomyTerm(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                 String termGroup,
                                 String codeSetKey,
                                 String termCode,
                                 String termLabel,
                                 String sourceField,
                                 NormalizedPolicyAggregate.Authority authority,
                                 int sortOrder) {
        String normalizedLabel = RawFieldValidator.normalize(termLabel);
        if (normalizedLabel == null) {
            return;
        }
        terms.add(NormalizedPolicyAggregate.TaxonomyTerm.builder()
                .termGroup(termGroup)
                .codeSetKey(codeSetKey)
                .termCode(termCode)
                .termLabel(normalizedLabel)
                .sourceField(sourceField)
                .authority(authority)
                .sortOrder(sortOrder)
                .build());
    }

    private void addRangeFact(List<NormalizedPolicyAggregate.Fact> facts,
                              String factGroup,
                              String factCode,
                              String factMergeKey,
                              String factLabel,
                              Integer rangeMin,
                              Integer rangeMax,
                              String unit,
                              String sourceField,
                              NormalizedPolicyAggregate.Authority authority,
                              BigDecimal confidence,
                              String evidenceText) {
        if (rangeMin == null && rangeMax == null) {
            return;
        }
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(factGroup)
                .factCodeSetKey(null)
                .factCode(factCode)
                .factMergeKey(factMergeKey)
                .factLabel(factLabel)
                .operator(rangeMin != null && rangeMax != null
                        ? NormalizedPolicyAggregate.Operator.RANGE
                        : rangeMin != null
                        ? NormalizedPolicyAggregate.Operator.GTE
                        : NormalizedPolicyAggregate.Operator.LTE)
                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                .rangeMinInt(rangeMin)
                .rangeMaxInt(rangeMax)
                .unit(unit)
                .sourceField(sourceField)
                .authority(authority)
                .confidence(confidence)
                .rawValue(firstNonBlank(
                        rangeMin == null ? null : String.valueOf(rangeMin),
                        rangeMax == null ? null : String.valueOf(rangeMax)
                ))
                .evidenceText(RawFieldValidator.normalize(evidenceText))
                .build());
    }

    private void addBoundaryFact(List<NormalizedPolicyAggregate.Fact> facts,
                                 String factGroup,
                                 String factCode,
                                 String factMergeKey,
                                 String factLabel,
                                 Integer intValue,
                                 NormalizedPolicyAggregate.Operator operator,
                                 String unit,
                                 String sourceField,
                                 NormalizedPolicyAggregate.Authority authority,
                                 BigDecimal confidence,
                                 String evidenceText) {
        if (intValue == null) {
            return;
        }
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(factGroup)
                .factCodeSetKey(null)
                .factCode(factCode)
                .factMergeKey(factMergeKey)
                .factLabel(factLabel)
                .operator(operator)
                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                .intValue(intValue)
                .unit(unit)
                .sourceField(sourceField)
                .authority(authority)
                .confidence(confidence)
                .rawValue(String.valueOf(intValue))
                .evidenceText(RawFieldValidator.normalize(evidenceText))
                .build());
    }

    private void addDateFact(List<NormalizedPolicyAggregate.Fact> facts,
                             String factGroup,
                             String factCode,
                             String factMergeKey,
                             String factLabel,
                             LocalDate dateValue,
                             String sourceField,
                             NormalizedPolicyAggregate.Authority authority,
                             BigDecimal confidence,
                             String evidenceText) {
        if (dateValue == null) {
            return;
        }
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(factGroup)
                .factCodeSetKey(null)
                .factCode(factCode)
                .factMergeKey(factMergeKey)
                .factLabel(factLabel)
                .operator(NormalizedPolicyAggregate.Operator.EQ)
                .valueType(NormalizedPolicyAggregate.ValueType.DATE)
                .dateValue(dateValue)
                .sourceField(sourceField)
                .authority(authority)
                .confidence(confidence)
                .rawValue(dateValue.toString())
                .evidenceText(RawFieldValidator.normalize(evidenceText))
                .build());
    }

    private record YouthMidPartition(
            List<String> officialLabels,
            List<String> rawAliases
    ) {
        private String summaryLabel() {
            return rawAliases.isEmpty() && officialLabels.size() == 1
                    ? officialLabels.get(0)
                    : null;
        }
    }
}
