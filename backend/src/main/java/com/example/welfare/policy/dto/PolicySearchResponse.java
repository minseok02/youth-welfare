package com.example.welfare.policy.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonDeserialize(builder = PolicySearchResponse.PolicySearchResponseBuilder.class)
public class PolicySearchResponse {

    private List<PolicySummaryResponse> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    private boolean hasNext;

    @JsonPOJOBuilder(withPrefix = "")
    public static class PolicySearchResponseBuilder {
    }
}
