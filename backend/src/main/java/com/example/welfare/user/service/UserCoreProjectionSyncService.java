package com.example.welfare.user.service;

import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserCoreProjectionCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserCoreProjectionSyncService {

    private final UserCoreProjectionCommandRepository userCoreProjectionCommandRepository;

    @Transactional
    public void syncAuthUser(User user, String userKey, String emailLookupHash) {
        userCoreProjectionCommandRepository.upsertAuthProjection(
                user,
                userKey,
                emailLookupHash
        );
    }

    @Transactional
    public void syncUserProfile(User user,
                                String userKey,
                                Integer age,
                                String ageBand,
                                LocalDateTime ageCalculatedAt,
                                boolean hasName,
                                boolean hasBirthDate) {
        userCoreProjectionCommandRepository.upsertUserProfileProjection(
                user,
                userKey,
                age,
                ageBand,
                ageCalculatedAt,
                hasName,
                hasBirthDate
        );
    }
}
