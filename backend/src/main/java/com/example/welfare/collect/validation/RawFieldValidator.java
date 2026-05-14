package com.example.welfare.collect.validation;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.dto.YouthApiDto;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 공공API 원시 데이터 품질 방어 유틸.
 * <p>
 * 책임 ①: 아이템이 저장할 가치가 있는지 판단 (isValid*)
 *   - source_id, title이 없으면 저장 불가 → skip
 *
 * 책임 ②: 문자열 정규화 (normalize)
 *   - null / blank → null 반환 (DB에 빈 문자열 저장 방지)
 *   - 앞뒤 공백·탭 제거
 *
 * 책임 ③: 날짜 방어 (isDateSane)
 *   - 실제 존재하는 날짜인지 확인 (파싱 성공 여부)
 *   - 1900 이전 또는 2100 이후 값은 오염 데이터로 간주, null 처리
 *
 * 책임 ④: FieldQualityStats 기록 (recordStats*)
 *   - 실제 API 응답이 생긴 후 null/빈값 필드 패턴 파악용
 */
@Slf4j
public final class RawFieldValidator {

    private static final LocalDate DATE_MIN = LocalDate.of(1900, 1, 1);
    private static final LocalDate DATE_MAX = LocalDate.of(2100, 12, 31);

    private RawFieldValidator() {}

    // ===== 필수 필드 검증 =====

    public static boolean isValidYouth(YouthApiDto.Item item) {
        if (item == null) return false;
        if (isBlank(item.getPlcyNo())) {
            log.warn("[Validator][YOUTH] plcyNo 없음 — skip");
            return false;
        }
        if (isBlank(item.getPlcyNm())) {
            log.warn("[Validator][YOUTH] title(plcyNm) 없음 plcyNo={} — skip", item.getPlcyNo());
            return false;
        }
        return true;
    }

    public static boolean isValidBokjiroCentral(BokjiroCentralDto.Item item) {
        if (item == null) return false;
        if (isBlank(item.getServId())) {
            log.warn("[Validator][BOKJIRO_CENTRAL] servId 없음 — skip");
            return false;
        }
        if (isBlank(item.getServNm())) {
            log.warn("[Validator][BOKJIRO_CENTRAL] title(servNm) 없음 servId={} — skip", item.getServId());
            return false;
        }
        return true;
    }

    public static boolean isValidBokjiroLocal(BokjiroLocalDto.Item item) {
        if (item == null) return false;
        if (isBlank(item.getServId())) {
            log.warn("[Validator][BOKJIRO_LOCAL] servId 없음 — skip");
            return false;
        }
        if (isBlank(item.getServNm())) {
            log.warn("[Validator][BOKJIRO_LOCAL] title(servNm) 없음 servId={} — skip", item.getServId());
            return false;
        }
        return true;
    }

    public static boolean isValidGov24(Gov24ServiceListDto.Item item) {
        if (item == null) return false;
        if (isBlank(item.getServiceId())) {
            log.warn("[Validator][GOV24] serviceId 없음 — skip");
            return false;
        }
        if (isBlank(item.getServiceName())) {
            log.warn("[Validator][GOV24] title(serviceName) 없음 serviceId={} — skip", item.getServiceId());
            return false;
        }
        return true;
    }

    // ===== 문자열 정규화 =====

    /**
     * null 또는 blank이면 null을 반환. 아니면 앞뒤 공백·탭 제거 후 반환.
     */
    public static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.strip();   // strip()은 유니코드 공백도 처리
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ===== 날짜 범위 방어 =====

    /**
     * 날짜 숫자열(yyyyMMdd 등)에서 숫자만 추출한 8자리를 파싱하여
     * 1900~2100 범위 내 실제 존재하는 날짜인지 확인한다.
     *
     * @param raw 날짜 문자열 (예: "20240101", "2024-01-01", "2024/01/01", "20240101120000")
     * @return 유효하면 true
     */
    public static boolean isDateSane(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 8) return false;
        try {
            LocalDate date = LocalDate.parse(digits.substring(0, 8),
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
            return !date.isBefore(DATE_MIN) && !date.isAfter(DATE_MAX);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    // ===== FieldQualityStats 기록 헬퍼 =====

    public static void recordStatsYouth(List<YouthApiDto.Item> items, FieldQualityStats stats) {
        for (YouthApiDto.Item item : items) {
            stats.record("plcyNo",          item.getPlcyNo());
            stats.record("title",           item.getPlcyNm());
            stats.record("description",     item.getPlcyExplnCn());
            stats.record("supportContent",  item.getPlcySprtCn());
            stats.record("category",        item.getLclsfNm());
            stats.record("keywords",        item.getPlcyKywdNm());
            stats.record("minAge",          item.getSprtTrgtMinAge());
            stats.record("maxAge",          item.getSprtTrgtMaxAge());
            stats.record("minIncome",       item.getEarnMinAmt());
            stats.record("maxIncome",       item.getEarnMaxAmt());
            stats.record("startDate",       item.getBizPrdBgngYmd());
            stats.record("endDate",         item.getBizPrdEndYmd());
            stats.record("aplyYmd",         item.getAplyYmd());
            stats.record("regionCd",        item.getZipCd());
            stats.record("detailUrl",       item.getAplyUrlAddr());
            stats.record("hostOrg",         item.getSprvsnInstCdNm());
            stats.record("inqCnt",          item.getInqCnt());

            // 날짜 파싱 건전성 검사
            if (!isDateSane(item.getBizPrdBgngYmd()))  stats.recordParseFail("startDate");
            if (!isDateSane(item.getBizPrdEndYmd()))   stats.recordParseFail("endDate");
        }
    }

    public static void recordStatsBokjiroCentral(List<BokjiroCentralDto.Item> items, FieldQualityStats stats) {
        for (BokjiroCentralDto.Item item : items) {
            stats.record("servId",          item.getServId());
            stats.record("title",           item.getServNm());
            stats.record("description",     item.getServDgst());
            stats.record("lifeArray",       item.getLifeArray());
            stats.record("intrsThema",      item.getIntrsThemaArray());
            stats.record("trgterIndvdl",    item.getTrgterIndvdlArray());
            stats.record("sprtCycNm",       item.getSprtCycNm());
            stats.record("srvPvsnNm",       item.getSrvPvsnNm());
            stats.record("onapPsbltYn",     item.getOnapPsbltYn());
            stats.record("detailUrl",       item.getServDtlLink());
            stats.record("hostOrg",         item.getJurMnofNm());
            stats.record("inqNum",          item.getInqNum());
            stats.record("registeredAt",    item.getSvcfrstRegTs());

            if (!isDateSane(item.getSvcfrstRegTs())) stats.recordParseFail("registeredAt");
        }
    }

    public static void recordStatsBokjiroLocal(List<BokjiroLocalDto.Item> items, FieldQualityStats stats) {
        for (BokjiroLocalDto.Item item : items) {
            stats.record("servId",          item.getServId());
            stats.record("title",           item.getServNm());
            stats.record("description",     item.getServDgst());
            stats.record("lifeNmArray",     item.getLifeNmArray());
            stats.record("intrsThemaNm",    item.getIntrsThemaNmArray());
            stats.record("trgterIndvdlNm",  item.getTrgterIndvdlNmArray());
            stats.record("sprtCycNm",       item.getSprtCycNm());
            stats.record("srvPvsnNm",       item.getSrvPvsnNm());
            stats.record("aplyMtdNm",       item.getAplyMtdNm());
            stats.record("detailUrl",       item.getServDtlLink());
            stats.record("sido",            item.getCtpvNm());
            stats.record("sgg",             item.getSggNm());
            stats.record("inqNum",          item.getInqNum());
            stats.record("lastModYmd",      item.getLastModYmd());
            stats.record("enfcBgngYmd",     item.getEnfcBgngYmd());
            stats.record("enfcEndYmd",      item.getEnfcEndYmd());

            if (!isDateSane(item.getLastModYmd()))    stats.recordParseFail("lastModYmd");
            if (!isDateSane(item.getEnfcBgngYmd()))   stats.recordParseFail("enfcBgngYmd");
            if (!isDateSane(item.getEnfcEndYmd()))    stats.recordParseFail("enfcEndYmd");
        }
    }

    public static void recordStatsGov24(List<Gov24ServiceListDto.Item> items, FieldQualityStats stats) {
        for (Gov24ServiceListDto.Item item : items) {
            stats.record("serviceId",          item.getServiceId());
            stats.record("title",              item.getServiceName());
            stats.record("servicePurpose",     item.getServicePurposeSummary());
            stats.record("supportTarget",      item.getSupportTarget());
            stats.record("selectionCriteria",  item.getSelectionCriteria());
            stats.record("supportContent",     item.getSupportContent());
            stats.record("applyMethod",        item.getApplyMethod());
            stats.record("applyDeadline",      item.getApplyDeadline());
            stats.record("detailUrl",          item.getDetailUrl());
            stats.record("hostOrg",            item.getManagingOrganizationName());
            stats.record("departmentName",     item.getDepartmentName());
            stats.record("supportType",        item.getSupportType());
            stats.record("userType",           item.getUserType());
            stats.record("serviceField",       item.getServiceField());
            stats.record("viewCount",          item.getViewCount());
            stats.record("registeredAt",       item.getRegisteredAt());
            stats.record("modifiedAt",         item.getModifiedAt());

            if (!isDateSane(item.getRegisteredAt())) stats.recordParseFail("registeredAt");
            if (!isDateSane(item.getModifiedAt()))   stats.recordParseFail("modifiedAt");
        }
    }

    // ===== 내부 유틸 =====

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
