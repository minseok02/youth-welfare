package com.example.welfare.collect.validation;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 복지로 목록 수집 시 청년 관련성이 낮은 정책을 1차로 제외한다.
 * <p>
 * 포함 조건:
 * - life_stage에 청년 신호가 있거나
 * - target_group에 청년 신호가 있거나
 * - 비정형 텍스트에서 추출한 나이 조건이 18~39와 겹치거나
 * - 제목/요약/대상 문구에 명시적인 청년 키워드가 있는 경우
 */
@Component
public class BokjiroYouthFilter {

    private static final int YOUTH_MIN_AGE = 18;
    private static final int YOUTH_MAX_AGE = 39;

    private static final List<String> YOUTH_KEYWORDS = List.of(
            "청년",
            "청년층",
            "대학생",
            "대학원생",
            "취업준비생",
            "취준생",
            "사회초년생",
            "미취업청년"
    );

    public boolean shouldCollect(BokjiroCentralDto.Item item) {
        if (item == null) {
            return false;
        }

        return hasYouthLifeStage(item.getLifeArray())
                || hasYouthTarget(item.getTrgterIndvdlArray())
                || overlapsYouthAge(item.getServNm(), item.getServDgst(), item.getTrgterIndvdlArray(), item.getLifeArray())
                || hasYouthTextSignal(item.getServNm(), item.getServDgst(), item.getTrgterIndvdlArray());
    }

    public boolean shouldCollect(BokjiroLocalDto.Item item) {
        if (item == null) {
            return false;
        }

        return hasYouthLifeStage(item.getLifeNmArray())
                || hasYouthTarget(item.getTrgterIndvdlNmArray())
                || overlapsYouthAge(item.getServNm(), item.getServDgst(), item.getTrgterIndvdlNmArray(), item.getLifeNmArray())
                || hasYouthTextSignal(item.getServNm(), item.getServDgst(), item.getTrgterIndvdlNmArray());
    }

    private boolean hasYouthLifeStage(String lifeStage) {
        return containsYouthKeyword(lifeStage);
    }

    private boolean hasYouthTarget(String targetGroup) {
        return containsYouthKeyword(targetGroup);
    }

    private boolean hasYouthTextSignal(String... texts) {
        return containsYouthKeyword(joinTexts(texts));
    }

    private boolean containsYouthKeyword(String text) {
        String normalized = RawFieldValidator.normalize(text);
        if (normalized == null) {
            return false;
        }
        return YOUTH_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private boolean overlapsYouthAge(String... texts) {
        TextConstraintExtractor.ConstraintSummary summary = TextConstraintExtractor.summarize(texts);
        Integer minAge = summary.minAge();
        Integer maxAge = summary.maxAge();

        if (minAge == null && maxAge == null) {
            return false;
        }
        if (minAge != null && minAge > YOUTH_MAX_AGE) {
            return false;
        }
        if (maxAge != null && maxAge < YOUTH_MIN_AGE) {
            return false;
        }
        return true;
    }

    private String joinTexts(String... texts) {
        if (texts == null || texts.length == 0) {
            return null;
        }

        StringBuilder joined = new StringBuilder();
        for (String text : texts) {
            String normalized = RawFieldValidator.normalize(text);
            if (normalized == null) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(' ');
            }
            joined.append(normalized);
        }
        return joined.isEmpty() ? null : joined.toString();
    }
}
