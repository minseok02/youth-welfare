package com.example.welfare.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SendChatMessageRequest {

    @NotBlank
    @Size(max = 2000, message = "메시지는 2000자 이하로 입력해야 합니다.")
    private String content;
}
