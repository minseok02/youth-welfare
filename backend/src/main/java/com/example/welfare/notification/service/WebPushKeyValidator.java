package com.example.welfare.notification.service;

import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class WebPushKeyValidator {

    public boolean isValidPublicKey(String publicKey) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(publicKey);
            return decoded.length == 65 && decoded[0] == 0x04;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isValidPrivateKey(String privateKey) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(privateKey);
            return decoded.length == 32;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
