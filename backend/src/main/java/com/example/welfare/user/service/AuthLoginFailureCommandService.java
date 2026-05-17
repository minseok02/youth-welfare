package com.example.welfare.user.service;

import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthLoginFailureCommandService {

    private final UserRepository userRepository;
    private final UserCoreSyncService userCoreSyncService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailedAttempt(Long userId, int maxLoginFail, int lockMinutes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다. userId=" + userId));

        user.increaseLoginFailCount();

        boolean locked = false;
        if (user.getLoginFailCount() >= maxLoginFail) {
            user.lock(LocalDateTime.now().plusMinutes(lockMinutes));
            locked = true;
        }

        userCoreSyncService.syncFromUser(user);
        return locked;
    }
}
