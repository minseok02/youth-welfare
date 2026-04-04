package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class UpdateProfileRequest {

    private String name;
    private LocalDate birthDate;
    private String phone;
    private String sido;
    private String sgg;
    private String regionCode;

    @Min(1) @Max(10)
    private Byte incomeLevel;

    private String householdType;
    private String employmentStatus;

    @Min(1) @Max(30)
    private Integer displayCount;

    private List<String> interestFields;
}
