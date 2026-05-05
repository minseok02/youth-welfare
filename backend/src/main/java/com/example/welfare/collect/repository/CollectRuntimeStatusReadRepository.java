package com.example.welfare.collect.repository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CollectRuntimeStatusReadRepository {

    Optional<LocalDateTime> findOpenUntil(String circuitKey);
}
