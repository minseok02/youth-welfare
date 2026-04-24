package com.example.welfare.policy.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PolicySearchResponse {

    private List<PolicySummaryResponse> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    private boolean hasNext;
}
