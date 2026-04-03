package com.nau.animalhospital.cs.dao;

import com.nau.animalhospital.cs.entity.ChatLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    List<ChatLog> findByUserIdAndTenantIdOrderByCreatedAtDesc(Long userId, Long tenantId);
}
