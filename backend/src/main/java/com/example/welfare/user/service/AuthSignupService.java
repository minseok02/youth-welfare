package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.SignupRequest;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSignupService {

    private final AuthIdentityReadService authIdentityReadService;
    private final PasswordEncoder passwordEncoder;
    private final UserRegistrationService userRegistrationService;
    private final AuthAdminRoleService authAdminRoleService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public void signup(SignupRequest request) {
        authAdminRoleService.validatePublicSignupEmail(request.getEmail());

        if (!emailVerificationService.isVerified(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        if (authIdentityReadService.existsByEmail(request.getEmail())) {
            return;
        }

        try {
            userRegistrationService.register(request, passwordEncoder.encode(request.getPassword()));
            emailVerificationService.clearVerified(request.getEmail());
        } catch (DataIntegrityViolationException e) {
            if (authIdentityReadService.existsByEmail(request.getEmail())) {
                return;
            }
            throw e;
        }
    }
}
