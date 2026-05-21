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
    private final UserPlainPiiReadService userPlainPiiReadService;
    private final AesEncryptUtil aesEncryptUtil;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void syncFromUser(User user) {
        String userKey = userKeyLookupService.findRequired(user.getId());
        UserPlainPii pii = userPlainPiiReadService.resolveCurrent(user, userKey);
        syncFromUser(user, userKey, pii);
    }

    @Transactional
    public void syncFromUser(User user, UserPlainPii pii) {
        String userKey = userKeyLookupService.findRequired(user.getId());
        syncFromUser(user, userKey, pii);
    }

    private void syncFromUser(User user, String userKey, UserPlainPii pii) {
        Integer age = calculateAge(pii.birthDate());
        String ageBand = resolveAgeBand(age);
        LocalDateTime ageCalculatedAt = age == null ? null : LocalDateTime.now();

        userCoreProjectionSyncService.syncAuthUser(user, userKey, pii.email());
        userCoreProjectionSyncService.syncUserProfile(
                user,
                userKey,
                age,
                ageBand,
                ageCalculatedAt,
                pii.hasName(),
                pii.hasBirthDate()
        );
        syncUserPii(user, userKey, pii);
    }

    private void syncUserPii(User user, String userKey, UserPlainPii pii) {
        userPiiSyncQueueService.enqueue(
                userKey,
                aesEncryptUtil.encrypt(pii.email()),
                aesEncryptUtil.encrypt(pii.name()),
                pii.birthDate() == null ? null : aesEncryptUtil.encrypt(pii.birthDate().toString()),
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
