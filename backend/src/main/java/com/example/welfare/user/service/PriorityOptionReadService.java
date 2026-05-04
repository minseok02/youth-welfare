package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.repository.PriorityOptionReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PriorityOptionReadService {

    private final PriorityOptionReadRepository priorityOptionReadRepository;

    public PriorityOption requireByCode(String code) {
        return priorityOptionReadRepository.findByCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
    }
}
