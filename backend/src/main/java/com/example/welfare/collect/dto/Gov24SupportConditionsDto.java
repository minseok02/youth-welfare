package com.example.welfare.collect.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gov24SupportConditionsDto {

    @JsonProperty("page")
    private Integer page;

    @JsonProperty("perPage")
    private Integer perPage;

    @JsonProperty("totalCount")
    private Integer totalCount;

    @JsonProperty("currentCount")
    private Integer currentCount;

    @JsonProperty("matchCount")
    private Integer matchCount;

    @JsonProperty("data")
    private List<Item> data;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        @JsonProperty("서비스ID")
        private String serviceId;

        @JsonProperty("서비스명")
        private String serviceName;

        private final Map<String, Object> conditions = new LinkedHashMap<>();

        public static Item fromRawPayload(String serviceId, String serviceName, Map<String, Object> conditions) {
            Item item = new Item();
            item.serviceId = serviceId;
            item.serviceName = serviceName;
            if (conditions != null) {
                item.conditions.putAll(conditions);
            }
            return item;
        }

        @JsonAnySetter
        void putCondition(String key, Object value) {
            if ("서비스ID".equals(key) || "서비스명".equals(key)) {
                return;
            }
            conditions.put(key, value);
        }
    }
}
