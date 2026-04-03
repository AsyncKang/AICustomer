package com.nau.animalhospital.cs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "username"}))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PostLoad
    @PrePersist
    private void normalizeTenant() {
        if (tenantId == null) {
            tenantId = 1L;
        }
    }
}
