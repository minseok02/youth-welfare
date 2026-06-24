package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSignupService {

    private final AuthIdentityReadService authIdentityReadService;
    private final PasswordEncoder passwordEncoder;
    private final UserRegistrationService userRegistrationService;
    private final AuthAdminRoleService authAdminRoleService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public void signup(SignupRequest request) {
        String emailHash = EmailLookupKeyGenerator.hash(request.getEmail());
        authAdminRoleService.validatePublicSignupEmail(request.getEmail());

        if (!emailVerificationService.isVerified(request.getEmail())) {
            log.warn("[AuthAudit] event=signup outcome=email_verification_required emailHash={}", emailHash);
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        if (authIdentityReadService.existsByEmail(request.getEmail())) {
            log.info("[AuthAudit] event=signup outcome=duplicate_accepted emailHash={}", emailHash);
            completeDuplicateSignup(request);
            return;
        }

        try {
            userRegistrationService.register(request, passwordEncoder.encode(request.getPassword()));
            emailVerificationService.clearVerified(request.getEmail());
            log.info("[AuthAudit] event=signup outcome=success emailHash={}", emailHash);
        } catch (DataIntegrityViolationException e) {
            if (authIdentityReadService.existsByEmail(request.getEmail())) {
                log.info("[AuthAudit] event=signup outcome=duplicate_accepted_after_conflict emailHash={}", emailHash);
                completeDuplicateSignup(request);
                return;
            }
            throw e;
        }
    }

    private void completeDuplicateSignup(SignupRequest request) {
        emailVerificationService.clearVerified(request.getEmail());
    }
}
