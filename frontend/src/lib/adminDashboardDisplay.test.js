import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  formatActorType,
  formatAdminDisplayText,
  formatAdminMessage,
  formatAdminOperationalText,
  formatAdminReviewNoteSuffix,
  formatAdminRoutePath,
  formatCodeOrStatus,
  formatCollectJobName,
  formatCompactJson,
  formatCodebookIntendedUse,
  formatCodebookMetadataValue,
  formatCodebookSetLabel,
  formatConfigLabel,
  formatConfigValue,
  formatDate,
  formatDateTime,
  formatMaskedEmail,
  formatMaskedUserKey,
  formatNumber,
  formatPercent,
  formatPolicyCorrectionScope,
  formatPolicyCorrectionType,
  formatPolicyDuplicateReviewClass,
  formatPolicyLinkReviewBucket,
  formatRelativeDateTime,
  formatSearchStatusFilter,
  formatSortKey,
  formatSourceIdLabel,
  formatSourceType,
  formatStatusLabel,
  humanizeAdminStatusKey,
  parseRegionCorrectionCodes,
  parseSuggestedRegionCodes,
  redactAdminDisplayText,
  resolveFieldCorrectionType,
} from "./adminDashboardDisplay.js";

describe("admin dashboard display helpers", () => {
  test("formats known labels and humanizes unknown status keys", () => {
    assert.equal(formatStatusLabel("READY_REAL_USER_TRAFFIC"), "실사용자 이용 데이터 충분");
    assert.equal(humanizeAdminStatusKey("READY_FOR_BOUNDED_PROMOTION_REVIEW"), "준비됨 / 대상 / 제한 범위 / 승격 / 검토");
    assert.equal(formatStatusLabel("CUSTOM_UNKNOWN_STATUS"), "CUSTOM / UNKNOWN / 상태");
    assert.equal(formatStatusLabel("NO_CANDIDATES"), "후보 없음");
    assert.equal(formatStatusLabel("RULE_ONLY"), "규칙 기반만 사용");
    assert.equal(formatStatusLabel("gateway_false"), "발송 시스템 실패");
    assert.equal(formatStatusLabel("web_push"), "웹푸시");
    assert.equal(formatSourceType("GOV24"), "정부24");
    assert.equal(formatActorType("ANONYMOUS"), "비회원");
    assert.equal(formatSearchStatusFilter("ACTIVE_ONLY"), "진행중만");
    assert.equal(formatSortKey("DEADLINE_ASC"), "마감임박순");
    assert.equal(formatPolicyLinkReviewBucket("benefit_support"), "지원금/급부형");
    assert.equal(formatPolicyDuplicateReviewClass("mirror_or_channel_variant_candidate"), "채널만 다른 중복 후보");
    assert.equal(formatPolicyCorrectionScope("TARGET_REGIONS"), "대상 지역 보정");
    assert.equal(formatPolicyCorrectionType("DETAIL_URL"), "상세 링크 보정");
    assert.equal(formatSourceIdLabel("abc-123"), "원천 ID abc-123");
    assert.equal(formatSourceType("xlsx"), "엑셀 파일");
    assert.equal(formatCodebookSetLabel("LOCAL_HOUSE_TENURE_TYPE"), "가옥(주거형태)코드");
    assert.equal(formatCodebookSetLabel("LOCAL_AGENCY_CODES"), "행정기관 코드");
    assert.equal(formatCodebookIntendedUse("user-profile housing tenure normalization"), "회원 주거형태 입력값 표준화");
    assert.equal(formatCodebookIntendedUse("gov24 agency normalization crosswalk"), "정부24 기관 코드 매핑/표준화");
    assert.equal(formatCodebookMetadataValue(135186), "135,186");
    assert.equal(formatCodeOrStatus("FAILED"), "실패");
    assert.equal(formatCodeOrStatus(null), "미분류");
    assert.equal(
      formatAdminOperationalText("false positive duplicate link backlog gap -> review queue"),
      "오탐 중복 링크 대기 항목 누락/불일치 → 검토 대기열",
    );
    assert.equal(
      formatAdminOperationalText("exact duplicate -> mirror variant -> benefit/support link review"),
      "완전 중복 → 채널만 다른 후보 → 급부형/지원형 링크 검토",
    );
    assert.equal(formatAdminOperationalText("priority passed -> failed"), "우선순위 정상 → 실패");
    assert.equal(
      formatAdminOperationalText("attempt 실패 breakdown과 최근 실패 endpoint를 확인합니다."),
      "시도 실패 상세 내역과 최근 실패 수신처를 확인합니다.",
    );
    assert.equal(
      formatAdminOperationalText("열린 circuit과 같은 source의 반복 실패면 safe reconcile 후 bounded hide 처리합니다."),
      "열린 회로와 같은 출처의 반복 실패면 안전 보정 후 범위 제한 숨김 처리합니다.",
    );
    assert.equal(
      formatAdminOperationalText("attention 표준코드 입력 backlog"),
      "주의 항목 선택 프로필 코드 보정 항목",
    );
  });

  test("formats numbers, percentages, compact json, and dates with fallbacks", () => {
    assert.equal(formatNumber(1234567), "1,234,567");
    assert.equal(formatNumber("bad"), "—");
    assert.equal(formatPercent(3.456), "3.46%");
    assert.equal(formatPercent("bad"), "—");
    assert.equal(formatCompactJson(""), "—");
    assert.equal(formatCompactJson("x".repeat(100)), `${"x".repeat(96)}...`);
    assert.equal(
      formatCompactJson('{"RULE_ONLY":2,"SCORED":1,"NOT_REQUESTED":0}'),
      "규칙 기반만 사용 2건 · AI 점수 반영 1건 · AI 미요청 0건",
    );
    assert.equal(
      formatCompactJson('{"gateway_false":3,"sent":10}'),
      "발송 시스템 실패 3건 · 발송 성공 10건",
    );
    assert.notEqual(formatDateTime("2026-06-25T10:30:00"), "—");
    assert.equal(formatDateTime("bad-date"), "—");
    assert.notEqual(formatDate("2026-06-25"), "—");
    assert.equal(formatDate("bad-date"), "—");
  });

  test("masks user identifiers and redacts admin display text", () => {
    assert.equal(formatMaskedUserKey("abcdef1234567890"), "abcdef…7890");
    assert.equal(formatMaskedUserKey("abcd"), "ab…cd");
    assert.equal(formatMaskedUserKey(""), "미연결");
    assert.equal(formatMaskedEmail("tester@example.com"), "te***@example.com");
    assert.equal(formatMaskedEmail("bad-email"), "이메일 형식 오류");
    assert.equal(
      redactAdminDisplayText("연락처 user@example.com 010-1234-5678 900101-1234567"),
      "연락처 [이메일] [전화번호] [식별번호]",
    );
    assert.equal(formatAdminDisplayText(" user@example.com 문의 "), "[이메일] 문의");
    assert.equal(formatAdminReviewNoteSuffix("010-1234-5678 확인"), " · [전화번호] 확인");
  });

  test("formats operational messages, config values, and correction helpers", () => {
    assert.equal(formatAdminMessage("notification gateway returned false"), "알림 발송 시스템이 실패를 반환했습니다");
    assert.equal(formatAdminMessage("rate limit timeout connection reset"), "요청 제한 응답 시간 초과 연결이 끊어짐");
    assert.equal(formatCollectJobName("GOV24_DETAIL"), "정부24 상세 수집");
    assert.equal(formatConfigLabel("Scheduler"), "자동 실행 일정");
    assert.equal(formatConfigLabel(null), "설정");
    assert.equal(formatConfigValue("max 100 items/run with 3 attempts"), "최대 100건/회 with 3회 시도");
    assert.deepEqual(parseRegionCorrectionCodes("11000, 26000\n27000"), ["11000", "26000", "27000"]);
    assert.deepEqual(parseSuggestedRegionCodes("서울(11000), 중복(11000), 부산(26000)"), ["11000", "26000"]);
    assert.equal(resolveFieldCorrectionType("BROKEN_LINK"), "DETAIL_URL");
    assert.equal(resolveFieldCorrectionType("OTHER"), null);
  });

  test("formats safe admin paths and relative update times", () => {
    assert.equal(formatAdminRoutePath("/admin?x=1", "fallback", "https://youthmoa.kr"), "/admin");
    assert.equal(formatAdminRoutePath("https://evil.example/admin", "fallback", "https://youthmoa.kr"), "fallback");
    assert.equal(formatAdminRoutePath("", "fallback", "https://youthmoa.kr"), "fallback");

    const now = new Date("2026-06-25T12:00:00Z");
    assert.equal(formatRelativeDateTime("2026-06-25T11:59:30Z", now), "방금 갱신");
    assert.equal(formatRelativeDateTime("2026-06-25T11:45:00Z", now), "15분 전 갱신");
    assert.equal(formatRelativeDateTime("2026-06-25T09:00:00Z", now), "3시간 전 갱신");
    assert.equal(formatRelativeDateTime("2026-06-23T12:00:00Z", now), "2일 전 갱신");
    assert.equal(formatRelativeDateTime("bad-date", now), "업데이트 정보 없음");
  });
});
