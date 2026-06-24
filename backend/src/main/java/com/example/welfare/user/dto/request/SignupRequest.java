package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Getter
public class SignupRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    @Pattern(regexp = AuthInputPolicy.EMAIL_REGEXP, message = AuthInputPolicy.EMAIL_MESSAGE)
    private String email;

    @NotBlank
    @Size(
            min = AuthInputPolicy.PASSWORD_MIN_LENGTH,
            max = AuthInputPolicy.PASSWORD_MAX_LENGTH,
            message = AuthInputPolicy.NEW_PASSWORD_MESSAGE
    )
    @Pattern(regexp = AuthInputPolicy.NEW_PASSWORD_REGEXP, message = AuthInputPolicy.NEW_PASSWORD_MESSAGE)
    private String password;

    @NotBlank
    @Pattern(regexp = "^[가-힣]{2,10}$", message = "이름은 특수 기호 및 숫자를 제외한 한글 2~10자로 입력해주세요.")
    private String name;

    @NotNull
    @Past
    private LocalDate birthDate;

    @NotNull(message = "개인정보 처리 안내 확인이 필요합니다.")
    @AssertTrue(message = "개인정보 처리 안내 확인이 필요합니다.")
    private Boolean privacyNoticeConfirmed;

    private Boolean optionalProfileConsentAgreed;

    private Boolean sensitiveInfoConsentAgreed;

    private String sido;

    private String sgg;

    /** 소득 분위 1~10 */
    @Min(1) @Max(10)
    private Byte incomeLevel;

    private String employmentStatus;

    private String householdType;

    private String houseTenureCode;

    private String housingTypeCode;

    private String basicLivingRecipientTypeCode;

    private String disabilityGradeCode;

    @Size(max = 5, message = "우선순위는 5개 이하로 설정 가능합니다.")
    private List<
            @NotBlank(message = "우선순위 코드는 비어 있을 수 없습니다.")
            @Size(max = 40, message = "우선순위 코드는 40자 이하여야 합니다.")
            @Pattern(regexp = "^[A-Z0-9_:-]+$", message = "우선순위 코드 형식이 올바르지 않습니다.")
            String> priorityCodes;

    @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
    public boolean isAtLeastFourteen() {
        if (birthDate == null) {
            return true;
        }
        return Period.between(birthDate, LocalDate.now()).getYears() >= 14;
    }

    @AssertTrue(message = "선택 개인정보 수집·이용 동의가 필요합니다.")
    public boolean isOptionalProfileConsentValid() {
        if (Boolean.TRUE.equals(optionalProfileConsentAgreed)) {
            return true;
        }
        return !hasText(sido)
                && !hasText(sgg)
                && incomeLevel == null
                && !hasText(employmentStatus)
                && !hasText(householdType)
                && !hasText(houseTenureCode)
                && !hasText(housingTypeCode)
                && !hasText(basicLivingRecipientTypeCode)
                && !hasAnyText(priorityCodes);
    }

    @AssertTrue(message = "민감정보 수집·이용 동의가 필요합니다.")
    public boolean isSensitiveInfoConsentValid() {
        return !hasText(disabilityGradeCode) || Boolean.TRUE.equals(sensitiveInfoConsentAgreed);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasAnyText(List<String> values) {
        return values != null && values.stream().anyMatch(this::hasText);
    }
}
