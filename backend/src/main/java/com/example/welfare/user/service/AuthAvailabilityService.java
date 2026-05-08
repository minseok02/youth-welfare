package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAvailabilityService {

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse checkEmailAvailability(String email) {
        return new EmailAvailabilityResponse(true);
    }
}
