package com.nau.animalhospital.cs.service;

import com.nau.animalhospital.cs.dao.TenantRepository;
import com.nau.animalhospital.cs.dao.UserRepository;
import com.nau.animalhospital.cs.dto.LoginRequest;
import com.nau.animalhospital.cs.dto.RegisterRequest;
import com.nau.animalhospital.cs.entity.Tenant;
import com.nau.animalhospital.cs.entity.User;
import com.nau.animalhospital.cs.security.JwtTokenUtil;
import com.nau.animalhospital.cs.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenUtil jwtTokenUtil;

    private Tenant resolveTenant(String tenantCode) {
        String code = tenantCode.trim();
        return tenantRepository.findByCode(code).orElseGet(() -> {
            Tenant t = new Tenant();
            t.setCode(code);
            t.setName(code);
            t.setCreatedAt(LocalDateTime.now());
            return tenantRepository.save(t);
        });
    }

    public Map<String, Object> register(RegisterRequest request) {
        String username = request.getUsername().trim();
        Tenant tenant = resolveTenant(request.getTenantCode());
        userRepository.findByTenantIdAndUsername(tenant.getId(), username).ifPresent(v -> {
            throw new RuntimeException("用户名已存在");
        });
        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(request.getPassword()));
        user.setRole("admin".equalsIgnoreCase(request.getRole()) ? "admin" : "user");
        user.setCreatedAt(LocalDateTime.now());
        user.setTenantId(tenant.getId());
        userRepository.save(user);
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername(), user.getRole(), user.getTenantId());
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("role", user.getRole());
        m.put("token", token);
        m.put("tenantId", user.getTenantId());
        m.put("tenantCode", tenant.getCode());
        return m;
    }

    public Map<String, Object> login(LoginRequest request) {
        String username = request.getUsername().trim();
        Tenant tenant = resolveTenant(request.getTenantCode());
        User user = userRepository.findByTenantIdAndUsername(tenant.getId(), username).orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername(), user.getRole(), user.getTenantId());
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("role", user.getRole());
        m.put("token", token);
        m.put("tenantId", user.getTenantId());
        m.put("tenantCode", tenant.getCode());
        return m;
    }
}
