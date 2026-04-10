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
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    @NotBlank
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
}
