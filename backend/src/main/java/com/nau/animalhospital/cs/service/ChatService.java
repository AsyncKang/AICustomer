package com.nau.animalhospital.cs.service;

import com.nau.animalhospital.cs.dao.ChatLogRepository;
import com.nau.animalhospital.cs.dto.ChatRequest;
import com.nau.animalhospital.cs.entity.ChatLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final PythonApiClient pythonApiClient;
    private final ChatLogRepository chatLogRepository;
    private final StringRedisTemplate redisTemplate;

    public Map<String, Object> chat(Long userId, Long tenantId, ChatRequest request) {
        String key = "qa:" + tenantId + ":" + request.getQuestion().trim();
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            saveLog(userId, tenantId, request.getQuestion(), cached);
            return Map.of("answer", cached, "fromCache", true);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("tenant_id", tenantId);
        body.put("question", request.getQuestion());
        Map<String, Object> response = pythonApiClient.ask(body);
        String answer = response.get("answer") != null ? String.valueOf(response.get("answer")) : "系统暂不可用";
        redisTemplate.opsForValue().set(key, answer, 10, TimeUnit.MINUTES);
        saveLog(userId, tenantId, request.getQuestion(), answer);
        return Map.of("answer", answer, "fromCache", false);
    }

    public List<ChatLog> listByUser(Long userId, Long tenantId) {
        return chatLogRepository.findByUserIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId);
    }

    private void saveLog(Long userId, Long tenantId, String question, String answer) {
        ChatLog log = new ChatLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setQuestion(question);
        log.setAnswer(answer);
        log.setCreatedAt(LocalDateTime.now());
        chatLogRepository.save(log);
    }
}
