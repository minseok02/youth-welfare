package com.example.welfare.user.service;

import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserCoreProjectionSyncService {

    private final AuthUserRepository authUserRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void syncAuthUser(User user, String userKey) {
        AuthUser authUser = authUserRepository.findByUserKey(userKey)
                .orElse(AuthUser.builder()
                        .userKey(userKey)
                        .build());
        authUser.syncFrom(user, EmailLookupKeyGenerator.hash(user.getEmail()));
        authUserRepository.save(authUser);
    }

    @Transactional
    public void syncUserProfile(User user,
                                String userKey,
                                Integer age,
                                String ageBand,
                                LocalDateTime ageCalculatedAt) {
        UserProfile userProfile = userProfileRepository.findByUserKey(userKey)
                .orElse(UserProfile.builder()
                        .userKey(userKey)
                        .build());
        userProfile.syncFrom(user, age, ageBand, ageCalculatedAt);
        userProfileRepository.save(userProfile);
    }
}
