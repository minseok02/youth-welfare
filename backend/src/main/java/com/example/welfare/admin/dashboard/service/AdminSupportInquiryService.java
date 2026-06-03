package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
import com.example.welfare.support.entity.SupportInquiry;
import com.example.welfare.support.repository.SupportInquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSupportInquiryService {

    private final SupportInquiryRepository supportInquiryRepository;

    @Transactional(readOnly = true)
    public AdminSupportInquiryResponse getRecentInquiries(Integer requestedLimit) {
        int limit = requestedLimit == null ? 10 : Math.max(1, Math.min(requestedLimit, 20));
        long openCount = supportInquiryRepository.countByStatus(SupportInquiry.Status.OPEN);
        List<AdminSupportInquiryResponse.Item> items = supportInquiryRepository
                .findByStatusOrderByCreatedAtDesc(SupportInquiry.Status.OPEN, PageRequest.of(0, limit))
                .stream()
                .map(inquiry -> new AdminSupportInquiryResponse.Item(
                        inquiry.getId(),
                        inquiry.getCategory().name(),
                        inquiry.getCategory().getLabel(),
                        inquiry.getContactEmail(),
                        inquiry.getMessage(),
                        inquiry.getRoutePath(),
                        inquiry.getUserKey(),
                        inquiry.getCreatedAt()
                ))
                .toList();
        return new AdminSupportInquiryResponse(openCount, items);
    }
}
