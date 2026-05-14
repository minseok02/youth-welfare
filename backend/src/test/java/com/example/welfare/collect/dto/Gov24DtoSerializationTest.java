package com.example.welfare.collect.dto;

import com.example.welfare.global.config.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Gov24DtoSerializationTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Test
    void serviceListItem_serializesAsObject() throws Exception {
        Gov24ServiceListDto.Item item = new Gov24ServiceListDto.Item();
        ReflectionTestUtils.setField(item, "serviceId", "351050000109");
        ReflectionTestUtils.setField(item, "serviceName", "저소득주민 국민건강보험료 지원");
        ReflectionTestUtils.setField(item, "viewCount", 90430L);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(item));

        assertThat(json.isObject()).isTrue();
        assertThat(json.get("서비스ID").asText()).isEqualTo("351050000109");
        assertThat(json.get("서비스명").asText()).isEqualTo("저소득주민 국민건강보험료 지원");
        assertThat(json.get("조회수").asLong()).isEqualTo(90430L);
    }

    @Test
    void serviceDetailItem_serializesAsObject() throws Exception {
        Gov24ServiceDetailDto.Item item = new Gov24ServiceDetailDto.Item();
        ReflectionTestUtils.setField(item, "serviceId", "351050000109");
        ReflectionTestUtils.setField(item, "serviceName", "저소득주민 국민건강보험료 지원");
        ReflectionTestUtils.setField(item, "servicePurpose", "상세 내용");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(item));

        assertThat(json.isObject()).isTrue();
        assertThat(json.get("서비스ID").asText()).isEqualTo("351050000109");
        assertThat(json.get("서비스명").asText()).isEqualTo("저소득주민 국민건강보험료 지원");
        assertThat(json.get("서비스목적").asText()).isEqualTo("상세 내용");
    }

    @Test
    void supportConditionsItem_serializesAsObject() throws Exception {
        Gov24SupportConditionsDto.Item item = new Gov24SupportConditionsDto.Item();
        ReflectionTestUtils.setField(item, "serviceId", "351050000109");
        ReflectionTestUtils.setField(item, "serviceName", "저소득주민 국민건강보험료 지원");

        @SuppressWarnings("unchecked")
        Map<String, Object> conditions = (Map<String, Object>) ReflectionTestUtils.getField(item, "conditions");
        conditions.put("JA0101", "Y");
        conditions.put("JA0111", 120);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(item));

        assertThat(json.isObject()).isTrue();
        assertThat(json.get("서비스ID").asText()).isEqualTo("351050000109");
        assertThat(json.get("서비스명").asText()).isEqualTo("저소득주민 국민건강보험료 지원");
        assertThat(json.get("conditions").get("JA0101").asText()).isEqualTo("Y");
        assertThat(json.get("conditions").get("JA0111").asInt()).isEqualTo(120);
    }

    @Test
    void supportConditionsRawMap_serializesAsExpected() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("서비스ID", "351050000109");
        payload.put("서비스명", "저소득주민 국민건강보험료 지원");
        payload.put("JA0101", "Y");
        payload.put("JA0111", 120);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(payload));

        assertThat(json.isObject()).isTrue();
        assertThat(json.get("서비스ID").asText()).isEqualTo("351050000109");
        assertThat(json.get("서비스명").asText()).isEqualTo("저소득주민 국민건강보험료 지원");
        assertThat(json.get("JA0101").asText()).isEqualTo("Y");
        assertThat(json.get("JA0111").asInt()).isEqualTo(120);
    }
}
