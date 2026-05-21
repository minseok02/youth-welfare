package com.example.welfare.user.service;

import java.time.LocalDate;

public record UserPlainPii(
        String email,
        String name,
        LocalDate birthDate
) {

    public boolean hasName() {
        return name != null && !name.trim().isEmpty();
    }

    public boolean hasBirthDate() {
        return birthDate != null;
    }
}
