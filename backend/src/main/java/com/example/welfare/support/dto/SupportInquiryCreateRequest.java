package com.example.welfare.support.dto;

import com.example.welfare.support.entity.SupportInquiry;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupportInquiryCreateRequest(
        @NotNull(message = "category는 필수입니다.")
        SupportInquiry.Category category,
        @NotBlank(message = "contactEmail은 필수입니다.")
        @Email(message = "contactEmail 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "contactEmail은 254자 이하여야 합니다.")
        String contactEmail,
        @NotBlank(message = "message는 필수입니다.")
        @Size(max = 2000, message = "message는 2000자 이하여야 합니다.")
        String message,
        @Size(max = 255, message = "routePath는 255자 이하여야 합니다.")
        @Pattern(regexp = "^$|^/(?!/)[^\\r\\n]*$", message = "routePath 형식이 올바르지 않습니다.")
        String routePath
) {
}
