package com.example.welfare.chat.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CreateChatSessionRequest {

    @Size(max = 100, message = "세션 제목은 100자 이하로 입력해야 합니다.")
    private String title;
}
