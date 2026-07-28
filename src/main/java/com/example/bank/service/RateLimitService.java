package com.example.bank.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public void check(String key, int limit, long windowSeconds) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit) {
                throw new IllegalStateException("Too many requests. Try again later.");
            }
            timestamps.addLast(Instant.now());
        }
    }
}
