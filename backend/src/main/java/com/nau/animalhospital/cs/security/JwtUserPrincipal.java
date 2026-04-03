package com.nau.animalhospital.cs.security;

public record JwtUserPrincipal(Long userId, String role, Long tenantId) {
}
