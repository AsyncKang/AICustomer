package com.nau.animalhospital.cs.config;

import com.nau.animalhospital.cs.service.DocumentIndexQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RedisStreamBootstrap implements ApplicationRunner {
    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public void run(ApplicationArguments args) {
        try {
            try (RedisConnection c = redisConnectionFactory.getConnection()) {
                c.streamCommands().xGroupCreate(
                        DocumentIndexQueueService.STREAM_KEY.getBytes(StandardCharsets.UTF_8),
                        DocumentIndexQueueService.GROUP,
                        ReadOffset.latest(),
                        true);
            }
        } catch (Exception ignored) {
        }
    }
}
