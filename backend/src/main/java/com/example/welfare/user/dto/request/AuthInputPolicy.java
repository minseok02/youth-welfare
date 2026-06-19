package com.example.welfare.user.dto.request;

public final class AuthInputPolicy {

    public static final int PASSWORD_MIN_LENGTH = 10;
    public static final int PASSWORD_MAX_LENGTH = 72;

    public static final String EMAIL_REGEXP =
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String EMAIL_MESSAGE =
            "이메일 형식이 올바르지 않습니다.";

    public static final String NEW_PASSWORD_REGEXP =
            "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{10,72}$";
    public static final String NEW_PASSWORD_MESSAGE =
            "비밀번호는 공백 없이 영문과 숫자를 포함한 10~72자로 입력해주세요.";

    private AuthInputPolicy() {
    }
}
