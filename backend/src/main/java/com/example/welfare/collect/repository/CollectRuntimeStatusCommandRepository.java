package com.example.welfare.collect.repository;

import java.time.LocalDateTime;

public interface CollectRuntimeStatusCommandRepository {

    void upsertOpenUntil(String circuitKey, LocalDateTime openUntil, LocalDateTime updatedAt);
}
