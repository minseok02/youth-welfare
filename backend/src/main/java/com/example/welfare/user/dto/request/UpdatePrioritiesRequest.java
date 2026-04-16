package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class UpdatePrioritiesRequest {

    @NotNull
    @Size(min = 1, max = 5, message = "우선순위는 1개 이상 5개 이하로 설정 가능합니다.")
    private List<String> priorityCodes;
}
