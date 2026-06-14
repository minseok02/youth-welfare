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

    /**
     * "해당하지 않음"(비수급·비장애)을 명시적으로 선택했음을 나타내는 sentinel.
     * 공식 코드북에는 없는 앱 레이어 값이며, "선택 안 함"(미응답, 빈 값)과 구분된다.
     * 추천 매칭에서는 미보유(null과 동일)로 취급한다. ({@code RecommendationMatchingSupport})
     */
    public static final String NOT_APPLICABLE_CODE = "NONE";

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
        if (NOT_APPLICABLE_CODE.equals(code) && supportsNotApplicable(codeSetKey)) {
            return;
        }
        if (!officialCodebookReadService.containsCode(codeSetKey, code)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean supportsNotApplicable(String codeSetKey) {
        return BASIC_LIVING_RECIPIENT_TYPE_CODE_SET.equals(codeSetKey)
                || DISABILITY_GRADE_CODE_SET.equals(codeSetKey);
    }
}
