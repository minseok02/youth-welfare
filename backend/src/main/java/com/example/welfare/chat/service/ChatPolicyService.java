package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.repository.ChatPolicyReadCondition;
import com.example.welfare.chat.repository.ChatPolicyReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatPolicyService {

    private static final int DEFAULT_CANDIDATE_LIMIT = 5;
    private static final int MAX_CANDIDATE_LIMIT = 10;
    private static final Pattern SEARCH_TOKEN_PATTERN = Pattern.compile("[0-9A-Za-z가-힣]+");

    private final ChatPolicyReadRepository chatPolicyReadRepository;

    @Transactional(readOnly = true)
    public List<ChatPolicyCandidate> findCandidates(String question) {
        return findCandidates(question, DEFAULT_CANDIDATE_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ChatPolicyCandidate> findCandidates(String question, int limit) {
        if (!StringUtils.hasText(question)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        int normalizedLimit = normalizeLimit(limit);
        String keyword = buildFulltextKeyword(question);

        return chatPolicyReadRepository.findCandidates(new ChatPolicyReadCondition(keyword, normalizedLimit)).stream()
                .map(ChatPolicyCandidate::from)
                .toList();
    }

    private String buildFulltextKeyword(String question) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = SEARCH_TOKEN_PATTERN.matcher(question);
        while (matcher.find()) {
            sb.append("+").append(matcher.group()).append(" ");
        }
        return sb.toString().trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_CANDIDATE_LIMIT;
        }
        return Math.min(limit, MAX_CANDIDATE_LIMIT);
    }
}
