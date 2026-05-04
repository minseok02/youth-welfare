package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;

public interface UserRegistrationCommandRepository {

    User save(User user);
}
