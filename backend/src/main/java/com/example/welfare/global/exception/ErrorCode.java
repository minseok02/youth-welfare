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

    // 회원
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U001", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U002", "존재하지 않는 회원입니다."),
    WITHDRAWN_USER(HttpStatus.GONE, "U003", "탈퇴한 회원입니다."),

    // 정책
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "존재하지 않는 정책입니다."),
    BOOKMARK_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "P002", "북마크는 최대 200건까지 저장할 수 있습니다."),

    // 추천
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "추천 정보를 찾을 수 없습니다."),
    SCORE_WEIGHT_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "R002", "추천 가중치 설정을 찾을 수 없습니다."),

    // 알림
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "N001", "알림 발송에 실패했습니다."),

    // 챗봇
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CH001", "존재하지 않는 챗 세션입니다."),

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
