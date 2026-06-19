package com.example.welfare.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SendChatMessageRequest {

    @NotBlank
    @Size(max = 2000, message = "메시지는 2000자 이하로 입력해야 합니다.")
    private String content;

    @Size(max = 64, message = "branchKey는 64자 이하로 입력해야 합니다.")
    @Pattern(regexp = "^$|^[A-Za-z0-9_-]+$", message = "branchKey 형식이 올바르지 않습니다.")
    private String branchKey;

    @Min(value = 1, message = "coachPolicyId는 1 이상이어야 합니다.")
    private Long coachPolicyId;
}
