package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectRuntimeStatusCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CollectRuntimeStatusCommandService {

    private final CollectRuntimeStatusCommandRepository collectRuntimeStatusCommandRepository;

    @Transactional
    public void openCircuit(String circuitKey, LocalDateTime openUntil) {
        collectRuntimeStatusCommandRepository.upsertOpenUntil(circuitKey, openUntil, LocalDateTime.now());
    }
}
