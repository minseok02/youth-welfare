package com.example.welfare.recommend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RecommendationAsyncConfig {

    @Bean(name = "recommendationAsyncExecutor", destroyMethod = "shutdown")
    public Executor recommendationAsyncExecutor(@Value("${recommend.async.parallelism:1}") int parallelism) {
        int effectiveParallelism = Math.max(1, parallelism);
        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "recommendation-async-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(effectiveParallelism, threadFactory);
    }
}
