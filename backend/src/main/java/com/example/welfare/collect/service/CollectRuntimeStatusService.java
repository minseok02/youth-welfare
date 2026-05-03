package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroLocalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectRuntimeStatusService {

    private final BokjiroLocalClient bokjiroLocalClient;

    public List<CircuitStatusSnapshot> getCircuitStatuses() {
        BokjiroLocalClient.RateLimitCircuitStatus status = bokjiroLocalClient.getRateLimitCircuitStatus();
        return List.of(new CircuitStatusSnapshot(
                "BOKJIRO_LOCAL",
                status.open(),
                status.remainingMs(),
                status.openUntil()
        ));
    }

    public record CircuitStatusSnapshot(
            String circuitKey,
            boolean open,
            long remainingMs,
            LocalDateTime openUntil
    ) {
    }
}
