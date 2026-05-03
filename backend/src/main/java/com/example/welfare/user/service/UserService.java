package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.dto.response.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserReadService userReadService;
    private final UserBookmarkReadService userBookmarkReadService;
    private final UserProfileCommandService userProfileCommandService;
    private final UserAccountCommandService userAccountCommandService;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return userReadService.getProfile(userId);
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> getBookmarks(Long userId) {
        return userBookmarkReadService.getBookmarks(userId);
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        userProfileCommandService.updateProfile(userId, request);
    }

    @Transactional
    public void updatePriorities(Long userId, UpdatePrioritiesRequest request) {
        userProfileCommandService.updatePriorities(userId, request);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        userAccountCommandService.changePassword(userId, currentPassword, newPassword);
    }

    @Transactional
    public void withdraw(Long userId, String password, String accessToken) {
        userAccountCommandService.withdraw(userId, password, accessToken);
    }

    @Transactional
    public void unsubscribeNotifications(Long userId) {
        userAccountCommandService.unsubscribeNotifications(userId);
    }

    @Transactional
    public void unsubscribeNotificationsByUserKey(String userKey) {
        userAccountCommandService.unsubscribeNotificationsByUserKey(userKey);
    }
}
