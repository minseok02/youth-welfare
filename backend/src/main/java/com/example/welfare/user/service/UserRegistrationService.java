package com.example.welfare.user.service;

import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRegistrationCommandRepository;
import com.example.welfare.user.util.UserEmailShadowValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRegistrationCommandRepository userRegistrationCommandRepository;
    private final UserCoreSyncService userCoreSyncService;
    private final UserAccountOriginResolver userAccountOriginResolver;

    @Transactional
    public void register(SignupRequest request, String encodedPassword) {
        String normalizedEmail = request.getEmail();
        User user = User.builder()
                .email(UserEmailShadowValue.from(normalizedEmail))
                .accountOrigin(userAccountOriginResolver.resolve(normalizedEmail))
                .passwordHash(encodedPassword)
                .sido(request.getSido())
                .sgg(request.getSgg())
                .incomeLevel(request.getIncomeLevel())
                .employmentStatus(request.getEmploymentStatus())
                .householdType(request.getHouseholdType())
                .build();

        userRegistrationCommandRepository.save(user);
        userCoreSyncService.syncFromUser(
                user,
                new UserPlainPii(
                        normalizedEmail,
                        request.getName(),
                        request.getBirthDate()
                )
        );
    }
}
