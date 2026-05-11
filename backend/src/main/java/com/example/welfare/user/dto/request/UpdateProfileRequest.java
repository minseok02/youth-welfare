package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class UpdateProfileRequest {

    @Pattern(regexp = "^[가-힣]{2,10}$", message = "이름은 특수 기호 및 숫자를 제외한 한글 2~10자로 입력해주세요.")
    private String name;
    private LocalDate birthDate;
    private String sido;
    private String sgg;
    private String regionCode;

    @Min(1) @Max(10)
    private Byte incomeLevel;

    private String householdType;
    private String employmentStatus;
    private Boolean notificationYn;
    private String notificationPeriod;

    @Min(0) @Max(1)
    private Double notificationMinScore;

    @Min(1) @Max(30)
    private Integer displayCount;

    private List<String> interestFields;
    private List<String> targetTypes;
}
