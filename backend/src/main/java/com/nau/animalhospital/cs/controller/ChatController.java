package com.nau.animalhospital.cs.controller;

import com.nau.animalhospital.cs.dto.ChatRequest;
import com.nau.animalhospital.cs.entity.ChatLog;
import com.nau.animalhospital.cs.security.JwtUserPrincipal;
import com.nau.animalhospital.cs.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public Map<String, Object> ask(@AuthenticationPrincipal JwtUserPrincipal principal,
                                   @Valid @RequestBody ChatRequest request) {
        return chatService.chat(principal.userId(), principal.tenantId(), request);
    }

    @GetMapping("/logs/me")
    public List<ChatLog> logs(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return chatService.listByUser(principal.userId(), principal.tenantId());
    }
}
