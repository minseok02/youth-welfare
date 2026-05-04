package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserAccountReadRepositoryImpl implements UserAccountReadRepository {

    private final UserRepository userRepository;

    @Override
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> findByUserKey(String userKey) {
        return userRepository.findByUserKey(userKey);
    }
}
