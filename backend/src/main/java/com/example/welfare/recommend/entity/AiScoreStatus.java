package com.example.welfare.recommend.entity;

/**
 * AI 점수 상태를 명시적으로 구분한다.
 *
 * NOT_REQUESTED: AI_TOP_N 밖이라 원래 AI 점수를 요청하지 않음
 * SCORED: AI 점수와 이유를 정상 수신
 * PARTIAL_MISSING: AI 호출은 했지만 특정 service_id 결과가 응답에서 누락됨
 * CALL_FAILED: AI 호출/파싱 전체가 실패함
 * RULE_ONLY: 강제 rule-only 모드 또는 OpenAI bypass
 */
public enum AiScoreStatus {
    NOT_REQUESTED,
    SCORED,
    PARTIAL_MISSING,
    CALL_FAILED,
    RULE_ONLY
}
