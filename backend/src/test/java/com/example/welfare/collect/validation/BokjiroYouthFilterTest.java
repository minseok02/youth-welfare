package com.example.welfare.collect.validation;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class BokjiroYouthFilterTest {

    private BokjiroYouthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BokjiroYouthFilter();
    }

    @Test
    void centralIncludesWhenLifeStageContainsYouth() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ReflectionTestUtils.setField(item, "servNm", "인문100년장학금");
        ReflectionTestUtils.setField(item, "lifeArray", "청년");

        assertThat(filter.shouldCollect(item)).isTrue();
    }

    @Test
    void centralIncludesWhenTargetGroupContainsYouthSignal() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ReflectionTestUtils.setField(item, "servNm", "구직활동 지원");
        ReflectionTestUtils.setField(item, "trgterIndvdlArray", "취업준비생,저소득");

        assertThat(filter.shouldCollect(item)).isTrue();
    }

    @Test
    void centralIncludesWhenExtractedAgeOverlapsYouthRange() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ReflectionTestUtils.setField(item, "servNm", "청년 전월세 지원");
        ReflectionTestUtils.setField(item, "servDgst", "만 19세 이상 34세 이하 무주택자에게 지원합니다.");

        assertThat(filter.shouldCollect(item)).isTrue();
    }

    @Test
    void localExcludesWhenNoYouthSignalExists() {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        ReflectionTestUtils.setField(item, "servNm", "노인복지민간단체지원");
        ReflectionTestUtils.setField(item, "servDgst", "노인들의 사회참여를 지원합니다.");
        ReflectionTestUtils.setField(item, "lifeNmArray", "노년");
        ReflectionTestUtils.setField(item, "trgterIndvdlNmArray", "보훈대상자");

        assertThat(filter.shouldCollect(item)).isFalse();
    }

    @Test
    void localIncludesWhenLifeStageMissingButTextExplicitlyMentionsYouth() {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        ReflectionTestUtils.setField(item, "servNm", "학자금 대출 연체자 신용회복 지원사업");
        ReflectionTestUtils.setField(item, "servDgst", "청년에게 신용회복 기회를 지원합니다.");

        assertThat(filter.shouldCollect(item)).isTrue();
    }

    @Test
    void localExcludesWhenExtractedAgeDoesNotOverlapYouthRange() {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        ReflectionTestUtils.setField(item, "servNm", "어르신 돌봄 지원");
        ReflectionTestUtils.setField(item, "servDgst", "만 65세 이상 주민에게 지원합니다.");

        assertThat(filter.shouldCollect(item)).isFalse();
    }
}
