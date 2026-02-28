package com.kailas.settlementengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);
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
            log.info("event=lock_acquired instanceId={}", INSTANCE_ID);
            return INSTANCE_ID;
        }

        return null;
    }

    public void releaseLock(String key, String lockId) {
        String value = redisTemplate.opsForValue().get(key);

        if (lockId != null && lockId.equals(value)) {
            redisTemplate.delete(key);
            currentLockId = null;
            log.info("event=lock_released instanceId={}", lockId);
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
