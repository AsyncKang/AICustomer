package com.nau.animalhospital.cs.dao;

import com.nau.animalhospital.cs.entity.Document;
import com.nau.animalhospital.cs.entity.DocumentIndexStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByIndexStatus(DocumentIndexStatus indexStatus);

    List<Document> findByTenantIdOrderByUploadedAtDesc(Long tenantId);

    Optional<Document> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Document> findByTenantIdAndNameAndFileType(Long tenantId, String name, String fileType);
}
