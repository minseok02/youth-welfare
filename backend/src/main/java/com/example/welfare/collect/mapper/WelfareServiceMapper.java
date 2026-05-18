package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.Gov24ServiceDetailDto;
import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.dto.Gov24SupportConditionsDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.support.BokjiroNormalizationSupport;
import com.example.welfare.collect.support.CollectCategorySupport;
import com.example.welfare.collect.support.NormalizationKeySupport;
import com.example.welfare.collect.support.YouthNormalizationSupport;
import com.example.welfare.collect.support.YouthOfficialCodeSupport;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+|www\\.[^\\s\"'<>]+");
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
                .unifiedCategory(CollectCategorySupport.mapYouthCompatCategory(
                        item.getLclsfNm(),
                        item.getPlcyNm(),
                        item.getPlcyExplnCn(),
                        item.getPlcyKywdNm()
                ))
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
        String provisionMethodLabel =
                YouthOfficialCodeSupport.resolveProvisionMethodLabel(item.getPlcyPvsnMthdCd(), service.getApplyMethodName());
        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .detail(buildDetail(service, null))
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .provisionMethod(provisionMethodLabel)
                        .summaryLabels(YouthNormalizationSupport.summaryLabels(service, youthMidPartition))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(YouthNormalizationSupport.taxonomyTerms(item, youthMidPartition))
                .facts(YouthNormalizationSupport.facts(service, item))
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

    public NormalizedPolicyAggregate toYouthDetailAggregate(WelfareService service, YouthApiDto.Item detail) {
        String resolvedUrl = firstNonBlank(detail.getAplyUrlAddr(), detail.getRefUrlAddr1(), detail.getRefUrlAddr2());
        boolean onlineApply = inferOnlineApply(resolvedUrl, detail.getPlcyAplyMthdCn());
        String provisionMethodLabel =
                YouthOfficialCodeSupport.resolveProvisionMethodLabel(detail.getPlcyPvsnMthdCd(), service.getApplyMethodName());
        return NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
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
                        .detailUrl(resolvedUrl)
                        .onlineApply(onlineApply)
                        .apiViewCount(service.getApiViewCount())
                        .registeredAt(service.getRegisteredAt())
                        .lastModifiedAt(service.getLastModifiedAt())
                        .build())
                .detail(NormalizedPolicyAggregate.Detail.builder()
                        .applyMethodDetail(firstNonBlank(detail.getPlcyAplyMthdCn(), service.getApplyMethodName()))
                        .onlineApplyUrl(onlineApply ? resolvedUrl : null)
                        .referenceUrlsJson(buildReferenceUrlsJson(referenceUrlCandidates(
                                referenceUrlCandidate(detail.getAplyUrlAddr(), "APPLY", "aplyUrlAddr", "신청 URL", 1.0d),
                                referenceUrlCandidate(detail.getRefUrlAddr1(), "REFERENCE", "refUrlAddr1", "참고 URL 1", 0.9d),
                                referenceUrlCandidate(detail.getRefUrlAddr2(), "REFERENCE", "refUrlAddr2", "참고 URL 2", 0.9d)
                        ), detail.getPlcyAplyMthdCn(), detail.getPlcySprtCn(), detail.getPlcyExplnCn()))
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .provisionMethod(provisionMethodLabel)
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .facts(YouthNormalizationSupport.facts(service, detail))
                .build();
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
                .isOnlineApply(inferOnlineApply(null, item.getAplyMtdNm()))
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

    // ===== Gov24 =====

    public WelfareService fromGov24(Gov24ServiceListDto.Item item) {
        TextConstraintExtractor.ConstraintSummary constraints = TextConstraintExtractor.summarize(
                item.getSupportTarget(),
                item.getSelectionCriteria(),
                item.getApplyDeadline()
        );
        String detailUrl = firstNormalizedUrl(item.getDetailUrl());
        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId(RawFieldValidator.normalize(item.getServiceId()))
                .title(stripAndNormalize(item.getServiceName()))
                .description(stripAndNormalize(item.getServicePurposeSummary()))
                .supportContent(firstNonBlank(
                        stripAndNormalize(item.getSupportContent()),
                        stripAndNormalize(item.getServicePurposeSummary())
                ))
                .categoryMain(RawFieldValidator.normalize(item.getServiceField()))
                .categorySub(RawFieldValidator.normalize(item.getUserType()))
                .keyword(RawFieldValidator.normalize(item.getSupportType()))
                .unifiedCategory(CollectCategorySupport.mapGov24CompatCategory(
                        item.getServiceField(),
                        item.getServiceName(),
                        item.getServicePurposeSummary(),
                        item.getSupportType()
                ))
                .hostOrg(RawFieldValidator.normalize(item.getManagingOrganizationName()))
                .operatingOrg(firstNonBlank(item.getDepartmentName(), item.getReceptionOrganization()))
                .minAge(constraints.minAge())
                .maxAge(constraints.maxAge())
                .applyEndDate(constraints.applyEndDate())
                .supportCycle(null)
                .provisionType(RawFieldValidator.normalize(item.getSupportType()))
                .applyMethodName(RawFieldValidator.normalize(item.getApplyMethod()))
                .isOnlineApply(inferOnlineApply(detailUrl, item.getApplyMethod()))
                .detailUrl(detailUrl)
                .apiViewCount(item.getViewCount() != null ? item.getViewCount() : 0L)
                .registeredAt(parseDateTimeLoose(item.getRegisteredAt()))
                .lastModifiedAt(parseDateTimeLoose(item.getModifiedAt()))
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    public NormalizedPolicyAggregate toNormalizedGov24(Gov24ServiceListDto.Item item) {
        WelfareService service = fromGov24(item);
        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .detail(buildDetail(service, null))
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .provisionMethod(service.getApplyMethodName())
                        .summaryLabels(summaryLabels(
                                NormalizationKeySupport.SUMMARY_KEY_GOV24_SERVICE_FIELD, item.getServiceField(),
                                NormalizationKeySupport.SUMMARY_KEY_GOV24_USER_TYPE, item.getUserType(),
                                NormalizationKeySupport.SUMMARY_KEY_GOV24_BENEFIT_TYPE, item.getSupportType()
                        ))
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .build();
    }

    public NormalizedPolicyAggregate toGov24DetailAggregate(WelfareService service, Gov24ServiceDetailDto.Item detail) {
        String onlineApplyUrl = normalizeUrl(detail.getOnlineApplySiteUrl());
        String detailUrl = firstNonBlank(service.getDetailUrl(), onlineApplyUrl);
        boolean onlineApply = onlineApplyUrl != null || inferOnlineApply(detailUrl, detail.getApplyMethod());
        return NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.valueOf(service.getSourceType().name()))
                        .sourceId(service.getSourceId())
                        .title(service.getTitle())
                        .summary(firstNonBlank(service.getDescription(), service.getSupportContent(), detail.getServicePurpose()))
                        .description(firstNonBlank(
                                stripAndNormalize(detail.getServicePurpose()),
                                service.getDescription()
                        ))
                        .supportContent(firstNonBlank(
                                stripAndNormalize(detail.getSupportContent()),
                                service.getSupportContent()
                        ))
                        .hostOrg(firstNonBlank(
                                RawFieldValidator.normalize(detail.getManagingOrganizationName()),
                                service.getHostOrg()
                        ))
                        .operatingOrg(firstNonBlank(
                                RawFieldValidator.normalize(detail.getReceptionOrganizationName()),
                                service.getOperatingOrg()
                        ))
                        .status(NormalizedPolicyAggregate.ServiceStatus.valueOf(service.getStatus().name()))
                        .startDate(service.getStartDate())
                        .endDate(service.getEndDate())
                        .applyStartDate(service.getApplyStartDate())
                        .applyEndDate(service.getApplyEndDate())
                        .detailUrl(detailUrl)
                        .onlineApply(onlineApply)
                        .apiViewCount(service.getApiViewCount())
                        .registeredAt(service.getRegisteredAt())
                        .lastModifiedAt(firstNonBlankDateTime(
                                parseDateTimeLoose(detail.getModifiedAt()),
                                service.getLastModifiedAt()
                        ))
                        .build())
                .detail(NormalizedPolicyAggregate.Detail.builder()
                        .targetDetail(firstNonBlank(
                                RawFieldValidator.normalize(detail.getSupportTarget()),
                                service.getDescription()
                        ))
                        .supportDetail(firstNonBlank(
                                stripAndNormalize(detail.getSupportContent()),
                                service.getSupportContent()
                        ))
                        .applyMethodDetail(firstNonBlank(
                                stripAndNormalize(detail.getApplyMethod()),
                                service.getApplyMethodName()
                        ))
                        .selectionCriteria(RawFieldValidator.normalize(detail.getSelectionCriteria()))
                        .requiredDocuments(joinLabeledLines(
                                "구비서류", detail.getRequiredDocuments(),
                                "공무원확인구비서류", detail.getPublicOfficerVerifiedDocuments(),
                                "본인확인필요구비서류", detail.getIdentityVerificationDocuments()
                        ))
                        .contactText(RawFieldValidator.normalize(detail.getContact()))
                        .legalBasisText(joinLabeledLines(
                                "행정규칙", detail.getAdministrativeRule(),
                                "자치법규", detail.getLocalRegulation(),
                                "법령", detail.getLaw()
                        ))
                        .onlineApplyUrl(onlineApplyUrl)
                        .referenceUrlsJson(buildReferenceUrlsJson(referenceUrlCandidates(
                                        referenceUrlCandidate(service.getDetailUrl(), "DETAIL", "detailUrl", "대표 상세 링크", 0.95d),
                                        referenceUrlCandidate(detail.getOnlineApplySiteUrl(), "APPLY", "onlineApplySiteUrl", "온라인 신청 사이트", 1.0d)
                                ),
                                detail.getApplyMethod(),
                                detail.getSupportContent(),
                                detail.getSelectionCriteria(),
                                detail.getSupportTarget(),
                                detail.getRequiredDocuments(),
                                detail.getAdministrativeRule(),
                                detail.getLocalRegulation(),
                                detail.getLaw()))
                        .supportCycle(service.getSupportCycle())
                        .provisionType(firstNonBlank(
                                RawFieldValidator.normalize(detail.getSupportType()),
                                service.getProvisionType()
                        ))
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory(service.getUnifiedCategory())
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .build();
    }

    public NormalizedPolicyAggregate toGov24SupportConditionsAggregate(WelfareService service, Gov24SupportConditionsDto.Item item) {
        return NormalizedPolicyAggregate.builder()
                .core(buildCore(service))
                .facts(gov24SupportConditionFacts(item))
                .build();
    }

    public List<ServiceTag> tagsFromGov24(Gov24ServiceListDto.Item item, WelfareService service) {
        List<ServiceTag> tags = new ArrayList<>();
        String serviceField = RawFieldValidator.normalize(item.getServiceField());
        if (serviceField != null) {
            tags.add(buildTag(service, ServiceTag.TagType.INTEREST_THEME, serviceField));
        }
        String userType = RawFieldValidator.normalize(item.getUserType());
        if (userType != null) {
            tags.add(buildTag(service, ServiceTag.TagType.TARGET_GROUP, userType));
        }
        String supportType = RawFieldValidator.normalize(item.getSupportType());
        if (supportType != null) {
            tags.add(buildTag(service, ServiceTag.TagType.KEYWORD, supportType));
        }
        addConstraintKeywordTags(tags, service,
                item.getSupportTarget(),
                item.getSelectionCriteria(),
                item.getSupportContent(),
                item.getApplyMethod());
        return tags;
    }

    public List<ServiceRegion> regionsFromGov24(Gov24ServiceListDto.Item item, WelfareService service) {
        return List.of();
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

    private LocalDateTime firstNonBlankDateTime(LocalDateTime preferred, LocalDateTime fallback) {
        return preferred != null ? preferred : fallback;
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
                .referenceUrlsJson(buildReferenceUrlsJson(referenceUrlCandidates(
                        referenceUrlCandidate(service.getDetailUrl(),
                                service.getIsOnlineApply() != null && service.getIsOnlineApply() ? "APPLY" : "DETAIL",
                                "detailUrl",
                                service.getIsOnlineApply() != null && service.getIsOnlineApply() ? "대표 신청 링크" : "대표 상세 링크",
                                0.95d)
                ),
                        detailPayload == null ? null : detailPayload.getApplyMethodDetail(),
                        detailPayload == null ? null : detailPayload.getSupportDetail(),
                        detailPayload == null ? null : detailPayload.getSelectionCriteria(),
                        detailPayload == null ? null : detailPayload.getTargetDetail()))
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

    private String buildReferenceUrlsJson(List<ReferenceUrlCandidate> directCandidates, String... texts) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ReferenceUrlCandidate> collected = new ArrayList<>();

        if (directCandidates != null) {
            for (ReferenceUrlCandidate candidate : directCandidates) {
                if (candidate == null || candidate.url() == null || !seen.add(candidate.url())) {
                    continue;
                }
                collected.add(candidate);
            }
        }

        if (texts != null) {
            for (int i = 0; i < texts.length; i++) {
                String sourceField = "textField" + i;
                if (i == 0) {
                    sourceField = "applyMethodDetail";
                } else if (i == 1) {
                    sourceField = "supportDetail";
                } else if (i == 2) {
                    sourceField = "selectionCriteria";
                } else if (i == 3) {
                    sourceField = "targetDetail";
                }
                addExtractedUrlCandidates(collected, seen, texts[i], sourceField);
            }
        }

        if (collected.isEmpty()) {
            return null;
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < collected.size(); i++) {
            ReferenceUrlCandidate candidate = collected.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"url\":\"").append(escapeJson(candidate.url())).append("\",")
                    .append("\"type\":\"").append(escapeJson(candidate.type())).append("\",")
                    .append("\"sourceField\":\"").append(escapeJson(candidate.sourceField())).append("\",")
                    .append("\"label\":\"").append(escapeJson(candidate.label())).append("\",")
                    .append("\"confidence\":").append(String.format(java.util.Locale.US, "%.2f", candidate.confidence()))
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    private List<ReferenceUrlCandidate> referenceUrlCandidates(ReferenceUrlCandidate... candidates) {
        List<ReferenceUrlCandidate> result = new ArrayList<>();
        if (candidates == null) {
            return result;
        }
        for (ReferenceUrlCandidate candidate : candidates) {
            if (candidate != null) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void addExtractedUrlCandidates(List<ReferenceUrlCandidate> collected,
                                           LinkedHashSet<String> seen,
                                           String text,
                                           String sourceField) {
        String normalizedText = RawFieldValidator.normalize(text);
        if (normalizedText == null) {
            return;
        }

        Matcher matcher = URL_PATTERN.matcher(normalizedText);
        while (matcher.find()) {
            String url = normalizeUrl(matcher.group());
            if (url == null || !seen.add(url)) {
                continue;
            }
            collected.add(new ReferenceUrlCandidate(
                    url,
                    "EXTRACTED_FROM_TEXT",
                    sourceField,
                    "본문 추출 링크",
                    0.55d
            ));
        }
    }

    private ReferenceUrlCandidate referenceUrlCandidate(String rawUrl,
                                                        String type,
                                                        String sourceField,
                                                        String label,
                                                        double confidence) {
        String url = normalizeUrl(rawUrl);
        if (url == null) {
            return null;
        }
        return new ReferenceUrlCandidate(url, type, sourceField, label, confidence);
    }

    private String normalizeUrl(String rawUrl) {
        String normalized = RawFieldValidator.normalize(rawUrl);
        if (normalized == null) {
            return null;
        }
        if (normalized.regionMatches(true, 0, "www.", 0, 4)) {
            return "https://" + normalized;
        }
        return normalized;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String joinLabeledLines(String... labeledValues) {
        if (labeledValues == null || labeledValues.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i + 1 < labeledValues.length; i += 2) {
            String label = labeledValues[i];
            String value = RawFieldValidator.normalize(labeledValues[i + 1]);
            if (label == null || value == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(label).append(": ").append(value);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private List<NormalizedPolicyAggregate.Fact> gov24SupportConditionFacts(Gov24SupportConditionsDto.Item item) {
        List<NormalizedPolicyAggregate.Fact> facts = new ArrayList<>();
        Map<String, Object> conditions = item.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return facts;
        }

        Integer minAge = parseIntegerObject(conditions.get("JA0110"));
        Integer maxAge = parseIntegerObject(conditions.get("JA0111"));
        if (minAge != null || maxAge != null) {
            facts.add(NormalizedPolicyAggregate.Fact.builder()
                    .factGroup(NormalizationKeySupport.FACT_GROUP_AGE)
                    .factCodeSetKey("GOV24_SUPPORT_CONDITION")
                    .factCode("GOV24_SUPPORT_CONDITION_AGE")
                    .factMergeKey("GOV24_AGE_ELIGIBILITY")
                    .factLabel("지원 연령")
                    .operator(NormalizedPolicyAggregate.Operator.RANGE)
                    .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                    .rangeMinInt(minAge)
                    .rangeMaxInt(maxAge)
                    .sourceField("JA0110/JA0111")
                    .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                    .confidence(BigDecimal.ONE)
                    .rawValue(joinRawValues(conditions.get("JA0110"), conditions.get("JA0111")))
                    .evidenceText(item.getServiceName())
                    .build());
        }

        gov24FlagFact(facts, conditions, "JA0101", "GENDER", "남성", "GOV24_GENDER:MALE");
        gov24FlagFact(facts, conditions, "JA0102", "GENDER", "여성", "GOV24_GENDER:FEMALE");

        gov24FlagFact(facts, conditions, "JA0201", "INCOME", "중위소득 0~50%", "GOV24_INCOME:JA0201");
        gov24FlagFact(facts, conditions, "JA0202", "INCOME", "중위소득 51~75%", "GOV24_INCOME:JA0202");
        gov24FlagFact(facts, conditions, "JA0203", "INCOME", "중위소득 76~100%", "GOV24_INCOME:JA0203");
        gov24FlagFact(facts, conditions, "JA0204", "INCOME", "중위소득 101~200%", "GOV24_INCOME:JA0204");
        gov24FlagFact(facts, conditions, "JA0205", "INCOME", "중위소득 200% 초과", "GOV24_INCOME:JA0205");

        gov24FlagFact(facts, conditions, "JA0317", "EDUCATION", "초등학생", "GOV24_EDUCATION:JA0317");
        gov24FlagFact(facts, conditions, "JA0318", "EDUCATION", "중학생", "GOV24_EDUCATION:JA0318");
        gov24FlagFact(facts, conditions, "JA0319", "EDUCATION", "고등학생", "GOV24_EDUCATION:JA0319");
        gov24FlagFact(facts, conditions, "JA0320", "EDUCATION", "대학생/대학원생", "GOV24_EDUCATION:JA0320");

        gov24FlagFact(facts, conditions, "JA0326", "EMPLOYMENT", "근로자/직장인", "GOV24_EMPLOYMENT:JA0326");
        gov24FlagFact(facts, conditions, "JA0327", "EMPLOYMENT", "구직자/실업자", "GOV24_EMPLOYMENT:JA0327");

        gov24FlagFact(facts, conditions, "JA0401", "HOUSEHOLD", "다문화가족", "GOV24_HOUSEHOLD:JA0401");
        gov24FlagFact(facts, conditions, "JA0402", "HOUSEHOLD", "북한이탈주민", "GOV24_HOUSEHOLD:JA0402");
        gov24FlagFact(facts, conditions, "JA0403", "HOUSEHOLD", "한부모가정/조손가정", "GOV24_HOUSEHOLD:JA0403");
        gov24FlagFact(facts, conditions, "JA0404", "HOUSEHOLD", "1인가구", "GOV24_HOUSEHOLD:JA0404");
        gov24FlagFact(facts, conditions, "JA0411", "HOUSEHOLD", "다자녀가구", "GOV24_HOUSEHOLD:JA0411");
        gov24FlagFact(facts, conditions, "JA0412", "HOUSEHOLD", "무주택세대", "GOV24_HOUSEHOLD:JA0412");
        gov24FlagFact(facts, conditions, "JA0413", "HOUSEHOLD", "신규전입", "GOV24_HOUSEHOLD:JA0413");
        gov24FlagFact(facts, conditions, "JA0414", "HOUSEHOLD", "확대가족", "GOV24_HOUSEHOLD:JA0414");

        gov24FlagFact(facts, conditions, "JA0328", "SPECIAL_GROUP", "장애인", "GOV24_SPECIAL:JA0328");
        gov24FlagFact(facts, conditions, "JA0329", "SPECIAL_GROUP", "국가보훈대상자", "GOV24_SPECIAL:JA0329");
        gov24FlagFact(facts, conditions, "JA0330", "SPECIAL_GROUP", "질병/질환자", "GOV24_SPECIAL:JA0330");

        return facts;
    }

    private void gov24FlagFact(List<NormalizedPolicyAggregate.Fact> facts,
                               Map<String, Object> conditions,
                               String code,
                               String factGroup,
                               String label,
                               String mergeKey) {
        Object raw = conditions.get(code);
        if (!isEnabledSupportCondition(raw)) {
            return;
        }
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(factGroup)
                .factCodeSetKey("GOV24_SUPPORT_CONDITION")
                .factCode(code)
                .factMergeKey(mergeKey)
                .factLabel(label)
                .operator(NormalizedPolicyAggregate.Operator.FLAG)
                .valueType(NormalizedPolicyAggregate.ValueType.BOOLEAN)
                .boolValue(Boolean.TRUE)
                .sourceField(code)
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .confidence(BigDecimal.ONE)
                .rawValue(String.valueOf(raw))
                .evidenceText(label)
                .build());
    }

    private Integer parseIntegerObject(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            int value = number.intValue();
            return value > 0 ? value : null;
        }
        String normalized = RawFieldValidator.normalize(String.valueOf(raw));
        if (normalized == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(normalized);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isEnabledSupportCondition(Object raw) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = RawFieldValidator.normalize(String.valueOf(raw));
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        return !("0".equals(lower) || "n".equals(lower) || "false".equals(lower) || "해당사항없음".equals(normalized));
    }

    private String joinRawValues(Object left, Object right) {
        StringBuilder builder = new StringBuilder();
        if (left != null) {
            builder.append("min=").append(left);
        }
        if (right != null) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append("max=").append(right);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private record ReferenceUrlCandidate(
            String url,
            String type,
            String sourceField,
            String label,
            double confidence
    ) {
    }

}
