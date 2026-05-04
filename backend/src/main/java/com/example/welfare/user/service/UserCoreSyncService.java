package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.event.UserPiiSyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class UserCoreSyncService {

    private final UserKeyLookupService userKeyLookupService;
    private final UserCoreProjectionSyncService userCoreProjectionSyncService;
    private final UserPiiSyncQueueService userPiiSyncQueueService;
    private final AesEncryptUtil aesEncryptUtil;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void syncFromUser(User user) {
        String userKey = userKeyLookupService.findRequired(user.getId());

        Integer age = calculateAge(user.getBirthDate());
        String ageBand = resolveAgeBand(age);
        LocalDateTime ageCalculatedAt = age == null ? null : LocalDateTime.now();

        userCoreProjectionSyncService.syncAuthUser(user, userKey);
        userCoreProjectionSyncService.syncUserProfile(user, userKey, age, ageBand, ageCalculatedAt);
        syncUserPii(user, userKey);
    }

    private void syncUserPii(User user, String userKey) {
        userPiiSyncQueueService.enqueue(
                userKey,
                aesEncryptUtil.encrypt(user.getEmail()),
                aesEncryptUtil.encrypt(user.getName()),
                user.getBirthDate() == null ? null : aesEncryptUtil.encrypt(user.getBirthDate().toString()),
                user.getPhoneEnc()
        );
        applicationEventPublisher.publishEvent(new UserPiiSyncRequestedEvent(userKey));
    }

    private Integer calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private String resolveAgeBand(Integer age) {
        if (age == null) {
            return null;
        }
        if (age < 19) return "UNDER_19";
        if (age <= 24) return "19_24";
        if (age <= 29) return "25_29";
        if (age <= 34) return "30_34";
        if (age <= 39) return "35_39";
        return "40_PLUS";
    }

}
