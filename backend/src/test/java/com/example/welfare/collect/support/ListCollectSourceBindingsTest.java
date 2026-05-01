package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ListCollectSourceBindingsTest {

    private final WelfareServiceMapper mapper = new WelfareServiceMapper();

    @Test
    @DisplayName("청년 binding은 source type, source id, aggregate identity를 일관되게 만든다")
    void youthBindingBuildsConsistentIdentity() {
        YouthApiDto.Item item = new YouthApiDto.Item();
        ReflectionTestUtils.setField(item, "plcyNo", "Y-100");
        ReflectionTestUtils.setField(item, "plcyNm", "청년 지원");
        ReflectionTestUtils.setField(item, "lclsfNm", "주거");

        ListCollectSourceBinding<YouthApiDto.Item> binding = ListCollectSourceBindings.youth(mapper);

        assertThat(binding.sourceType()).isEqualTo(WelfareService.SourceType.YOUTH);
        assertThat(binding.sourceId(item)).isEqualTo("Y-100");
        assertThat(binding.toSaveCommand(item).incoming().getSourceId()).isEqualTo("Y-100");
        assertThat(binding.toSaveCommand(item).aggregate().core().sourceId()).isEqualTo("Y-100");
    }

    @Test
    @DisplayName("복지로 local binding은 source type과 source id를 일관되게 만든다")
    void bokjiroLocalBindingBuildsConsistentIdentity() {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        ReflectionTestUtils.setField(item, "servId", "LOCAL-100");
        ReflectionTestUtils.setField(item, "servNm", "청년 문화패스");

        ListCollectSourceBinding<BokjiroLocalDto.Item> binding = ListCollectSourceBindings.bokjiroLocal(mapper);

        assertThat(binding.sourceType()).isEqualTo(WelfareService.SourceType.BOKJIRO_LOCAL);
        assertThat(binding.sourceId(item)).isEqualTo("LOCAL-100");
        assertThat(binding.toSaveCommand(item).incoming().getSourceId()).isEqualTo("LOCAL-100");
        assertThat(binding.toSaveCommand(item).aggregate().core().sourceId()).isEqualTo("LOCAL-100");
    }
}
