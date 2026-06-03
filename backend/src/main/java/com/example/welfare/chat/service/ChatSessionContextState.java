package com.example.welfare.chat.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionContextState {

    private HousingContext housing;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HousingContext {
        private String activeBranchKey;
        private String anchorQuestion;
        private List<String> recentTopics;
        private List<String> recentPolicyTitles;
        private List<Long> recentPolicyIds;
        private List<String> suggestedBranchKeys;
    }
}
