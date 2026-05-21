package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;

import java.time.LocalDateTime;

public interface UserCoreProjectionCommandRepository {

    void upsertAuthProjection(User user, String userKey, String emailLookupHash);

    void upsertUserProfileProjection(User user,
                                     String userKey,
                                     Integer age,
                                     String ageBand,
                                     LocalDateTime ageCalculatedAt,
                                     boolean hasName,
                                     boolean hasBirthDate);
}
