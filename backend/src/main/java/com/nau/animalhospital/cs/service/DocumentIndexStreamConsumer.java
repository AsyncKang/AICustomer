package com.nau.animalhospital.cs.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentIndexStreamConsumer {
    private final StringRedisTemplate redisTemplate;
    private final DocumentIndexProcessor processor;

    @Scheduled(fixedDelay = 500)
    public void poll() {
        try {
            pollInner();
        } catch (Exception ignored) {
        }
    }

    private void pollInner() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(DocumentIndexQueueService.GROUP, "c1"),
                StreamReadOptions.empty().count(10).block(Duration.ofMillis(800)),
                StreamOffset.create(DocumentIndexQueueService.STREAM_KEY, ReadOffset.from(">")));
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            Object docIdObj = record.getValue().get("docId");
            String docIdStr = docIdObj != null ? docIdObj.toString() : null;
            if (docIdStr == null) {
                redisTemplate.opsForStream().acknowledge(DocumentIndexQueueService.STREAM_KEY, DocumentIndexQueueService.GROUP, record.getId());
                continue;
            }
            try {
                processor.process(Long.parseLong(docIdStr));
            } finally {
                redisTemplate.opsForStream().acknowledge(DocumentIndexQueueService.STREAM_KEY, DocumentIndexQueueService.GROUP, record.getId());
            }
        }
    }
}
