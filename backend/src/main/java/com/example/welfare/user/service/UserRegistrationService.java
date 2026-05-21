package com.example.welfare.user.service;

import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRegistrationCommandRepository;
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
        User user = User.builder()
                .email(request.getEmail())
                .accountOrigin(userAccountOriginResolver.resolve(request.getEmail()))
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
                        request.getEmail(),
                        request.getName(),
                        request.getBirthDate()
                )
        );
    }
}
