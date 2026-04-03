package com.nau.animalhospital.cs.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class DocumentIndexQueueService {
    public static final String STREAM_KEY = "doc:index";
    public static final String GROUP = "doc-index-group";

    private final StringRedisTemplate redisTemplate;

    public void enqueue(Long docId) {
        redisTemplate.opsForStream().add(STREAM_KEY, Collections.singletonMap("docId", String.valueOf(docId)));
    }
}
