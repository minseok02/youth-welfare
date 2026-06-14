package com.example.welfare.support.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.support.dto.MySupportInquiryResponse;
import com.example.welfare.support.repository.SupportInquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportInquiryQueryService {

    private static final int MAX_ITEMS = 50;

    private final SupportInquiryRepository supportInquiryRepository;

    /**
     * 로그인 사용자가 로그인 상태로 제출한 문의만 최신순으로 반환한다.
     * (로그아웃 상태에서 제출한 문의는 userId가 비어 있어 포함되지 않는다.)
     */
    @Transactional(readOnly = true)
    public List<MySupportInquiryResponse> getMyInquiries(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return supportInquiryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_ITEMS))
                .stream()
                .map(MySupportInquiryResponse::from)
                .toList();
    }
}
