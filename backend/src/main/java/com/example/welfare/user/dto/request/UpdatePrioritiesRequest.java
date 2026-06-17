package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class UpdatePrioritiesRequest {

    private Boolean optionalProfileConsentAgreed;

    @NotNull
    @Size(min = 1, max = 5, message = "우선순위는 1개 이상 5개 이하로 설정 가능합니다.")
    private List<
            @NotBlank(message = "우선순위 코드는 비어 있을 수 없습니다.")
            @Size(max = 40, message = "우선순위 코드는 40자 이하여야 합니다.")
            @Pattern(regexp = "^[A-Z0-9_:-]+$", message = "우선순위 코드 형식이 올바르지 않습니다.")
            String> priorityCodes;
}
