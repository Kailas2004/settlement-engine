package com.kailas.settlementengine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String acquireLock(String key, long timeoutSeconds) {

        String lockId = UUID.randomUUID().toString();

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, lockId, Duration.ofSeconds(timeoutSeconds));

        if (Boolean.TRUE.equals(success)) {
            System.out.println("Lock acquired by instance: " + lockId);
            return lockId;
        }

        return null;
    }

    public void releaseLock(String key, String lockId) {

        String currentLockId = redisTemplate.opsForValue().get(key);

        if (lockId != null && lockId.equals(currentLockId)) {
            redisTemplate.delete(key);
            System.out.println("Lock released by instance: " + lockId);
        }
    }
}
