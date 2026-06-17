package com.example.welfare.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PasswordResetRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    @Pattern(regexp = AuthInputPolicy.EMAIL_REGEXP, message = AuthInputPolicy.EMAIL_MESSAGE)
    private String email;
}
