package com.example.welfare.notification.service;

import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;

@Component
public class WebPushServiceFactoryImpl implements WebPushServiceFactory {

    @PostConstruct
    void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public PushService create(String publicKey, String privateKey, String subject)
            throws GeneralSecurityException, JoseException, IOException {
        return new PushService(publicKey, privateKey, subject);
    }
}
