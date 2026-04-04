package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class UpdatePrioritiesRequest {

    @NotNull
    @Size(max = 5, message = "우선순위는 최대 5개까지 설정 가능합니다.")
    private List<String> priorityCodes;
}
