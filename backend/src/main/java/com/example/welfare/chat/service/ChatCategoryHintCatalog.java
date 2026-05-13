package com.example.welfare.chat.service;

import com.example.welfare.global.util.SearchKeywordSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ChatCategoryHintCatalog {

    private static final List<CategoryHintDefinition> DEFINITIONS = List.of(
            new CategoryHintDefinition(
                    "금융·생활지원",
                    Set.of("금융", "생활비", "대출", "저축", "적금", "이자", "채무", "융자"),
                    List.of("금융", "생활비", "대출", "지원금")
            ),
            new CategoryHintDefinition(
                    "문화·여가",
                    Set.of("문화", "예술", "활동비", "관람", "축제", "동아리", "공연", "전시"),
                    List.of("문화", "예술", "활동비", "관람")
            ),
            new CategoryHintDefinition(
                    "건강·의료",
                    Set.of("정신건강", "상담", "의료", "의료비", "치료", "병원", "건강", "심리"),
                    List.of("정신건강", "상담", "의료비", "건강")
            ),
            new CategoryHintDefinition(
                    "가족·돌봄",
                    Set.of("돌봄", "보육", "임신", "출산", "양육", "육아", "가족"),
                    List.of("돌봄", "보육", "출산", "양육")
            )
    );

    public Optional<CategoryHint> infer(String question) {
        if (!StringUtils.hasText(question)) {
            return Optional.empty();
        }

        String normalized = SearchKeywordSupport.normalizeText(question);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }

        return DEFINITIONS.stream()
                .map(definition -> match(normalized, definition))
                .filter(MatchedHint::matched)
                .max(Comparator.comparingInt(MatchedHint::score))
                .map(matched -> new CategoryHint(matched.definition().preferredCategory(), matched.definition().searchTerms()));
    }

    private MatchedHint match(String normalizedQuestion, CategoryHintDefinition definition) {
        int score = 0;
        for (String trigger : definition.triggers()) {
            if (normalizedQuestion.contains(trigger)) {
                score++;
            }
        }
        return new MatchedHint(definition, score > 0, score);
    }

    public record CategoryHint(
            String preferredCategory,
            List<String> searchTerms
    ) {
    }

    private record CategoryHintDefinition(
            String preferredCategory,
            Set<String> triggers,
            List<String> searchTerms
    ) {
    }

    private record MatchedHint(
            CategoryHintDefinition definition,
            boolean matched,
            int score
    ) {
    }
}
