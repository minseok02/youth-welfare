package com.example.welfare.user.repository;

import java.time.LocalDate;

public interface UserLegacyPiiSourceReadModel {

    String getUserKey();

    String getEmail();

    String getName();

    LocalDate getBirthDate();
}
