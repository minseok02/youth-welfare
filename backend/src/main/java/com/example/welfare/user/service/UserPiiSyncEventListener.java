package com.example.welfare.user.service;

import com.example.welfare.user.event.UserPiiSyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserPiiSyncEventListener {

    private final UserPiiSyncProcessor userPiiSyncProcessor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserPiiSyncRequestedEvent event) {
        userPiiSyncProcessor.process(event.userKey());
    }
}
