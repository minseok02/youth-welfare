package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthAvailabilityService {

    private final AuthIdentityReadService authIdentityReadService;

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse checkEmailAvailability(String email) {
        return new EmailAvailabilityResponse(!authIdentityReadService.existsByEmail(email));
    }
}
