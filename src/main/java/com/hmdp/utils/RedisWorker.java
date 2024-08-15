package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {
    private static final int COUNT_BITS = 32;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPre) {
        LocalDateTime begin = LocalDateTime.of(2024, 8, 15, 17, 34);
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        Long timeStamp = nowSeconds - begin.toEpochSecond(ZoneOffset.UTC);
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPre +":"+ date);
        return timeStamp << COUNT_BITS | count;
    }
}
