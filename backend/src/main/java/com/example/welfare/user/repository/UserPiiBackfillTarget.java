package com.example.welfare.user.repository;

import java.time.LocalDate;

public interface UserPiiBackfillTarget {

    String getUserKey();

    String getEmail();

    String getName();

    LocalDate getBirthDate();

    String getEmailEnc();

    String getNameEnc();

    String getBirthDateEnc();
}
