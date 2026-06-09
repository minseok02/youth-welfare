package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class UpdateProfileRequest {

    @Pattern(regexp = "^[가-힣]{2,10}$", message = "이름은 특수 기호 및 숫자를 제외한 한글 2~10자로 입력해주세요.")
    private String name;

    @Past(message = "생년월일은 과거 날짜여야 합니다.")
    private LocalDate birthDate;

    @Size(max = 30, message = "시도는 30자 이하여야 합니다.")
    private String sido;

    @Size(max = 30, message = "시군구는 30자 이하여야 합니다.")
    private String sgg;

    @Size(max = 20, message = "지역코드는 20자 이하여야 합니다.")
    private String regionCode;

    @Min(1) @Max(10)
    private Byte incomeLevel;

    @Size(max = 50, message = "가구 유형은 50자 이하여야 합니다.")
    private String householdType;

    @Size(max = 50, message = "고용 상태는 50자 이하여야 합니다.")
    private String employmentStatus;

    @Size(max = 20, message = "주거 점유 코드는 20자 이하여야 합니다.")
    private String houseTenureCode;

    @Size(max = 20, message = "주거 유형 코드는 20자 이하여야 합니다.")
    private String housingTypeCode;

    @Size(max = 20, message = "기초생활수급자 유형 코드는 20자 이하여야 합니다.")
    private String basicLivingRecipientTypeCode;

    @Size(max = 20, message = "장애 등급 코드는 20자 이하여야 합니다.")
    private String disabilityGradeCode;

    private Boolean notificationYn;
    private Boolean notificationEmailYn;
    private Boolean notificationInAppYn;
    private Boolean notificationWebPushYn;

    @Pattern(regexp = "(?i)^(DAILY|WEEKLY|NONE)$", message = "알림 주기는 DAILY, WEEKLY, NONE 중 하나여야 합니다.")
    private String notificationPeriod;

    @Min(0) @Max(1)
    private Double notificationMinScore;

    @Min(1) @Max(30)
    private Integer displayCount;

    @Size(max = 20, message = "관심분야는 20개 이하로 입력해주세요.")
    private List<@Size(max = 50, message = "관심분야 값은 50자 이하여야 합니다.") String> interestFields;

    @Size(max = 20, message = "특수대상은 20개 이하로 입력해주세요.")
    private List<@Size(max = 50, message = "특수대상 값은 50자 이하여야 합니다.") String> targetTypes;
}
