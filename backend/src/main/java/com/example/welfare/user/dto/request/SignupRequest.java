package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class SignupRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
    private String password;

    @NotBlank
    @Pattern(regexp = "^[가-힣]{2,10}$", message = "이름은 특수 기호 및 숫자를 제외한 한글 2~10자로 입력해주세요.")
    private String name;

    @NotNull
    @Past
    private LocalDate birthDate;

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
}
