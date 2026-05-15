package com.example.welfare.notification.service;

import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface WebPushServiceFactory {

    nl.martijndwars.webpush.PushService create(String publicKey, String privateKey, String subject)
            throws GeneralSecurityException, JoseException, IOException;
}
