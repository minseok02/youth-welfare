package com.example.welfare.user.service;

import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final UserCoreSyncService userCoreSyncService;

    @Transactional
    public void register(SignupRequest request, String encodedPassword) {
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .sido(request.getSido())
                .sgg(request.getSgg())
                .incomeLevel(request.getIncomeLevel())
                .employmentStatus(request.getEmploymentStatus())
                .householdType(request.getHouseholdType())
                .build();

        userRepository.save(user);
        userCoreSyncService.syncFromUser(user);
    }
}
