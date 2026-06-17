package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PasswordResetConfirmRequest {

    @NotBlank
    @Size(max = 100)
    private String token;

    @NotBlank
    @Size(
            min = AuthInputPolicy.PASSWORD_MIN_LENGTH,
            max = AuthInputPolicy.PASSWORD_MAX_LENGTH,
            message = AuthInputPolicy.NEW_PASSWORD_MESSAGE
    )
    @Pattern(regexp = AuthInputPolicy.NEW_PASSWORD_REGEXP, message = AuthInputPolicy.NEW_PASSWORD_MESSAGE)
    private String newPassword;
}
