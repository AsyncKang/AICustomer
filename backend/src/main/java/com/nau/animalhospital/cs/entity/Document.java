package com.nau.animalhospital.cs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 32)
    private String fileType;

    @Column(nullable = false, length = 512)
    private String filePath;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentIndexStatus indexStatus = DocumentIndexStatus.UPLOADED;

    @Column(length = 1024)
    private String indexError;

    private LocalDateTime lastIndexAttemptAt;

    @Column(nullable = false)
    private int indexRetryCount = 0;

    private LocalDateTime lastIndexedAt;

    @PostLoad
    @PrePersist
    private void normalizeLegacy() {
        if (indexStatus == null) {
            indexStatus = DocumentIndexStatus.INDEXED;
        }
        if (tenantId == null) {
            tenantId = 1L;
        }
    }
}
