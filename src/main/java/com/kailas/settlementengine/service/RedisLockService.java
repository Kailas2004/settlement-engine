package com.kailas.settlementengine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    private volatile String currentLockId = null;
    private final String INSTANCE_ID = UUID.randomUUID().toString();

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String acquireLock(String key, long timeoutSeconds) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, INSTANCE_ID, Duration.ofSeconds(timeoutSeconds));

        if (Boolean.TRUE.equals(success)) {
            currentLockId = INSTANCE_ID;
            System.out.println("Lock acquired by instance: " + INSTANCE_ID);
            return INSTANCE_ID;
        }

        return null;
    }

    public void releaseLock(String key, String lockId) {
        String value = redisTemplate.opsForValue().get(key);

        if (lockId != null && lockId.equals(value)) {
            redisTemplate.delete(key);
            currentLockId = null;
            System.out.println("Lock released by instance: " + lockId);
        }
    }

    // ✅ Expose lock status
    public boolean isLockHeld(String key) {
        return redisTemplate.hasKey(key);
    }

    public String getCurrentLockHolder(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}