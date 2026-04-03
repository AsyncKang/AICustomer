package com.nau.animalhospital.cs.controller;

import com.nau.animalhospital.cs.dto.LoginRequest;
import com.nau.animalhospital.cs.dto.RegisterRequest;
import com.nau.animalhospital.cs.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }
}
