package com.nau.animalhospital.cs.dao;

import com.nau.animalhospital.cs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);
}
