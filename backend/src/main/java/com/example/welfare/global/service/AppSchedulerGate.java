package com.example.welfare.global.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppSchedulerGate {

    @Value("${app.scheduler.enabled:true}")
    private boolean enabled;

    public boolean shouldRun(String jobName) {
        if (enabled) {
            return true;
        }
        log.debug("[AppSchedulerGate] scheduled job skipped on this node job={}", jobName);
        return false;
    }
}
