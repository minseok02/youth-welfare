package com.example.welfare.user.repository;

import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class UserCoreProjectionCommandRepositoryImpl implements UserCoreProjectionCommandRepository {

    private final AuthUserRepository authUserRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    public void upsertAuthProjection(User user, String userKey, String emailLookupHash) {
        AuthUser authUser = authUserRepository.findByUserKey(userKey)
                .orElse(AuthUser.builder()
                        .userKey(userKey)
                        .build());
        authUser.syncFrom(user, emailLookupHash);
        authUserRepository.save(authUser);
    }

    @Override
    public void upsertUserProfileProjection(User user,
                                            String userKey,
                                            Integer age,
                                            String ageBand,
                                            LocalDateTime ageCalculatedAt,
                                            boolean hasName,
                                            boolean hasBirthDate) {
        UserProfile userProfile = userProfileRepository.findByUserKey(userKey)
                .orElse(UserProfile.builder()
                        .userKey(userKey)
                        .build());
        userProfile.syncFrom(user, age, ageBand, ageCalculatedAt, hasName, hasBirthDate);
        userProfileRepository.save(userProfile);
    }
}
