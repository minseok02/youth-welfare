package com.example.welfare.notification.gateway;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.KakaoOption;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoAlimtalkClient {

    @Value("${coolsms.api-key}")
    private String apiKey;

    @Value("${coolsms.api-secret}")
    private String apiSecret;

    @Value("${coolsms.from-number}")
    private String fromNumber;

    private DefaultMessageService messageService;

    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
    }

    /**
     * 카카오 알림톡 발송
     * @param phone 수신자 전화번호 (복호화된 원문)
     * @param templateId 카카오 알림톡 템플릿 코드
     * @param variables 템플릿 변수 (예: #{정책명})
     */
    public void send(String phone, String templateId, java.util.Map<String, String> variables) {
        try {
            Message message = new Message();
            message.setFrom(fromNumber);
            message.setTo(phone);

            KakaoOption kakaoOption = new KakaoOption();
            kakaoOption.setPfId(templateId); // 플러스친구 ID
            kakaoOption.setVariables(variables);
            message.setKakaoOptions(kakaoOption);

            messageService.sendOne(new SingleMessageSendingRequest(message));
            log.info("[KakaoAlimtalkClient] 발송 성공 to={}", maskPhone(phone));

        } catch (Exception e) {
            log.warn("[KakaoAlimtalkClient] 발송 실패 to={}: {}", maskPhone(phone), e.getMessage());
            throw new CustomException(ErrorCode.NOTIFICATION_SEND_FAILED);
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, phone.length() - 4) + "****";
    }
}
