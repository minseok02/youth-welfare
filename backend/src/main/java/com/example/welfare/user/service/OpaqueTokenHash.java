package com.example.welfare.user.service;

import com.example.welfare.global.util.RedisKeyHash;

final class OpaqueTokenHash {

    private OpaqueTokenHash() {
    }

    static String sha256Hex(String token) {
        return RedisKeyHash.sha256Hex(token);
    }
}
