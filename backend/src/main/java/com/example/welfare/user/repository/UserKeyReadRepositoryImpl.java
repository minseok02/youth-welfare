package com.example.welfare.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserKeyReadRepositoryImpl implements UserKeyReadRepository {

    private final UserRepository userRepository;

    @Override
    public Optional<String> findUserKeyById(Long userId) {
        return userRepository.findUserKeyById(userId);
    }

    @Override
    public Optional<Long> findIdByUserKey(String userKey) {
        return userRepository.findIdByUserKey(userKey);
    }
}
