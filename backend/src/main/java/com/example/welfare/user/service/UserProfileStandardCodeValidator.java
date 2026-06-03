package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.reference.service.OfficialCodebookReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileStandardCodeValidator {

    public static final String HOUSE_TENURE_CODE_SET = "LOCAL_HOUSE_TENURE_TYPE";
    public static final String HOUSING_TYPE_CODE_SET = "LOCAL_HOUSING_TYPE";
    public static final String BASIC_LIVING_RECIPIENT_TYPE_CODE_SET = "LOCAL_BASIC_LIVING_RECIPIENT_TYPE";
    public static final String DISABILITY_GRADE_CODE_SET = "LOCAL_DISABILITY_GRADE";

    private final OfficialCodebookReadService officialCodebookReadService;

    public void validateProfileCodes(String houseTenureCode,
                                     String housingTypeCode,
                                     String basicLivingRecipientTypeCode,
                                     String disabilityGradeCode) {
        validateCode(HOUSE_TENURE_CODE_SET, houseTenureCode);
        validateCode(HOUSING_TYPE_CODE_SET, housingTypeCode);
        validateCode(BASIC_LIVING_RECIPIENT_TYPE_CODE_SET, basicLivingRecipientTypeCode);
        validateCode(DISABILITY_GRADE_CODE_SET, disabilityGradeCode);
    }

    private void validateCode(String codeSetKey, String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        if (!officialCodebookReadService.containsCode(codeSetKey, code)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
