package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WelfareServiceMapperTest {

    private final WelfareServiceMapper mapper = new WelfareServiceMapper();

    @Test
    void fromYouth_infersOnlineApplyFromDetailUrl() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y001");
        setField(item, "plcyNm", "청년 주거 지원");
        setField(item, "plcyExplnCn", "정책 소개");
        setField(item, "plcySprtCn", "지원 내용");
        setField(item, "lclsfNm", "주거");
        setField(item, "aplyUrlAddr", "https://example.com/apply");

        WelfareService service = mapper.fromYouth(item);

        assertThat(service.getIsOnlineApply()).isTrue();
    }

    @Test
    void fromBokjiroLocal_extractsAgeAndOnlineApplyFromText() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servId", "L001");
        setField(item, "servNm", "청년 월세 지원");
        setField(item, "servDgst", "만 19세 이상 34세 이하 청년에게 월세를 지원합니다.");
        setField(item, "intrsThemaNmArray", "주거");
        setField(item, "aplyMtdNm", "온라인 신청 가능");

        WelfareService service = mapper.fromBokjiroLocal(item);

        assertThat(service.getMinAge()).isEqualTo(19);
        assertThat(service.getMaxAge()).isEqualTo(34);
        assertThat(service.getIsOnlineApply()).isTrue();
    }

    @Test
    void fromBokjiroCentral_extractsAgeFromDescription() throws Exception {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        setField(item, "servId", "C001");
        setField(item, "servNm", "청년 취업 지원");
        setField(item, "servDgst", "만 18세 이상 39세 이하 청년 대상 지원");
        setField(item, "intrsThemaArray", "일자리");

        WelfareService service = mapper.fromBokjiroCentral(item);

        assertThat(service.getMinAge()).isEqualTo(18);
        assertThat(service.getMaxAge()).isEqualTo(39);
    }

    @Test
    void fromBokjiroLocal_setsSupportContentAndApplyEndDateFallback() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servId", "L002");
        setField(item, "servNm", "청년 문화 지원");
        setField(item, "servDgst", "문화 활동비 지원. 신청기간 2026.01.01 ~ 2026.12.31");
        setField(item, "intrsThemaNmArray", "문화·여가");
        setField(item, "srvPvsnNm", "현금");

        WelfareService service = mapper.fromBokjiroLocal(item);

        assertThat(service.getSupportContent()).isEqualTo("문화 활동비 지원. 신청기간 2026.01.01 ~ 2026.12.31");
        assertThat(service.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
