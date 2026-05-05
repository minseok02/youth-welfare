package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectRuntimeStatusReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectRuntimeStatusService {

    public static final String BOKJIRO_LOCAL_CIRCUIT_KEY = "BOKJIRO_LOCAL";

    private final CollectRuntimeStatusReadRepository collectRuntimeStatusReadRepository;

    public List<CircuitStatusSnapshot> getCircuitStatuses() {
        return List.of(getCircuitStatus(BOKJIRO_LOCAL_CIRCUIT_KEY, LocalDateTime.now()));
    }

    public CircuitStatusSnapshot getCircuitStatus(String circuitKey, LocalDateTime now) {
        LocalDateTime openUntil = collectRuntimeStatusReadRepository.findOpenUntil(circuitKey).orElse(null);
        boolean open = openUntil != null && openUntil.isAfter(now);
        long remainingMs = open ? Duration.between(now, openUntil).toMillis() : 0L;
        return new CircuitStatusSnapshot(circuitKey, open, remainingMs, open ? openUntil : null);
    }

    public record CircuitStatusSnapshot(
            String circuitKey,
            boolean open,
            long remainingMs,
            LocalDateTime openUntil
    ) {
    }
}
