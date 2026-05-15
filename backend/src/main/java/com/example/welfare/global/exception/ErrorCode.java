package com.example.welfare.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 오류가 발생했습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "C003", "접근 권한이 없습니다."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "만료된 토큰입니다."),
    REUSED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "이미 사용된 Refresh Token입니다. 재로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A004", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "A005", "로그인 실패 횟수 초과로 계정이 잠겼습니다. 30분 후 다시 시도하세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A006", "로그인이 필요합니다."),
    ADMIN_EMAIL_SIGNUP_FORBIDDEN(HttpStatus.FORBIDDEN, "A007", "관리자 이메일은 공개 회원가입으로 생성할 수 없습니다."),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "A008", "유효하지 않거나 만료된 비밀번호 재설정 토큰입니다."),
    PASSWORD_RESET_EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "A009", "비밀번호 재설정 메일 발송에 실패했습니다."),
    AUTH_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "A010", "요청이 너무 많습니다. 잠시 후 다시 시도하세요."),
    EMAIL_VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "A011", "인증코드가 올바르지 않거나 만료되었습니다."),
    EMAIL_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "A012", "이메일 인증이 완료되지 않았습니다."),
    EMAIL_VERIFICATION_SEND_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "A013", "인증코드 요청이 너무 많습니다. 잠시 후 다시 시도하세요."),

    // 회원
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U001", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "존재하지 않는 회원입니다."),
    WITHDRAWN_USER(HttpStatus.GONE, "U003", "탈퇴한 회원입니다."),

    // 정책
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "존재하지 않는 정책입니다."),
    BOOKMARK_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "P002", "북마크는 최대 200건까지 저장할 수 있습니다."),
    POLICY_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "P003", "요청이 너무 많습니다. 잠시 후 다시 시도하세요."),

    // 추천
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "추천 정보를 찾을 수 없습니다."),
    SCORE_WEIGHT_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "R002", "추천 가중치 설정을 찾을 수 없습니다."),
    RECOMMENDATION_ALREADY_RUNNING(HttpStatus.CONFLICT, "R003", "이미 같은 사용자에 대한 추천 생성이 진행 중입니다."),

    // 알림
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "N001", "알림 발송에 실패했습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N002", "존재하지 않는 알림입니다."),
    NOTIFICATION_PUSH_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "N003", "존재하지 않는 웹푸시 구독입니다."),
    NOTIFICATION_PUSH_PUBLIC_KEY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "N004", "웹푸시 공개키가 아직 설정되지 않았습니다."),
    NOTIFICATION_PUSH_PUBLIC_KEY_INVALID(HttpStatus.SERVICE_UNAVAILABLE, "N005", "웹푸시 공개키 형식이 올바르지 않습니다."),

    // 챗봇
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CH001", "존재하지 않는 챗 세션입니다."),
    CHAT_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "CH002", "짧은 시간에 너무 많은 챗 요청이 발생했습니다. 잠시 후 다시 시도하세요."),

    // 수집
    COLLECT_API_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "COL001", "공공 API 수집에 실패했습니다."),
    COLLECT_ALREADY_RUNNING(HttpStatus.CONFLICT, "COL002", "이미 다른 수집 작업이 실행 중입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
