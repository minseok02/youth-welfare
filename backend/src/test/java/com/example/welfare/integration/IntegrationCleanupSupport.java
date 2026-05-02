package com.example.welfare.integration;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;

import java.util.function.Consumer;
import java.util.function.Predicate;

final class IntegrationCleanupSupport {

    private IntegrationCleanupSupport() {
    }

    static void cleanupUsers(UserRepository userRepository,
                             Predicate<User> filter,
                             Consumer<String> cleanupByUserKey,
                             Consumer<Long> cleanupByUserId) {
        userRepository.findAll().stream()
                .filter(filter)
                .forEach(user -> {
                    String userKey = userRepository.findUserKeyById(user.getId()).orElse(null);
                    if (userKey != null) {
                        cleanupByUserKey.accept(userKey);
                    }
                    if (cleanupByUserId != null) {
                        cleanupByUserId.accept(user.getId());
                    }
                    userRepository.delete(user);
                });
    }

    static void cleanupServices(WelfareServiceRepository welfareServiceRepository,
                                Predicate<WelfareService> filter,
                                Consumer<WelfareService> cleanupService) {
        welfareServiceRepository.findAll().stream()
                .filter(filter)
                .forEach(cleanupService);
    }
}
