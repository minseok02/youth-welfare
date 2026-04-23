package com.example.welfare.collect.service;

public interface CollectRetrySupport {

    @FunctionalInterface
    interface CheckedRunnable {
        void run() throws Exception;
    }
}
