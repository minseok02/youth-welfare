package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class WithdrawRequest {

    @NotBlank
    @Size(max = 100)
    private String password;
}
