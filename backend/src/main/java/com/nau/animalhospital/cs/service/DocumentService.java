package com.nau.animalhospital.cs.service;

import com.nau.animalhospital.cs.config.AppProperties;
import com.nau.animalhospital.cs.dao.DocumentRepository;
import com.nau.animalhospital.cs.entity.Document;
import com.nau.animalhospital.cs.entity.DocumentIndexStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final AppProperties appProperties;
    private final PythonApiClient pythonApiClient;
    private final DocumentIndexQueueService documentIndexQueueService;

    private Path resolveUploadRoot() {
        String u = appProperties.getUploadDir();
        Path p = Paths.get(u);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        String cwd = System.getProperty("user.dir");
        Path root = Paths.get(cwd);
        if (Files.exists(root.resolve("pom.xml"))) {
            return root.resolve(u).normalize().toAbsolutePath();
        }
        if (Files.exists(root.resolve("backend").resolve("pom.xml"))) {
            return root.resolve("backend").resolve(u).normalize().toAbsolutePath();
        }
        return root.resolve(u).normalize().toAbsolutePath();
    }

    private void validateMime(MultipartFile file, String baseName) {
        String ct = file.getContentType();
        if (ct == null || ct.isBlank()) {
            return;
        }
        if (baseName.endsWith(".pdf")) {
            if (!ct.contains("pdf") && !ct.contains("octet-stream")) {
                throw new RuntimeException("文件类型与扩展名不符");
            }
        } else if (baseName.endsWith(".md")) {
            if (!ct.contains("markdown") && !ct.contains("text/plain") && !ct.contains("text/") && !ct.contains("octet-stream")) {
                throw new RuntimeException("文件类型与扩展名不符");
            }
        }
    }

    @Transactional
    public Document upload(MultipartFile file, String category, Long tenantId, boolean overwrite) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new RuntimeException("文件名无效");
        }
        String baseName = Paths.get(originalName).getFileName().toString();
        if (!baseName.endsWith(".md") && !baseName.endsWith(".pdf")) {
            throw new RuntimeException("仅支持md/pdf");
        }
        String fileType = baseName.endsWith(".md") ? "md" : "pdf";
        validateMime(file, baseName);
        documentRepository.findByTenantIdAndNameAndFileType(tenantId, baseName, fileType).ifPresent(existing -> {
            if (!overwrite) {
                throw new RuntimeException("文件已存在，是否覆盖？");
            }
            try {
                delete(existing.getId(), tenantId);
            } catch (IOException e) {
                throw new RuntimeException("无法替换已有文件");
            }
        });
        Path dir = resolveUploadRoot().resolve(String.valueOf(tenantId)).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String newName = UUID.randomUUID() + "_" + baseName;
        Path target = dir.resolve(newName).normalize().toAbsolutePath();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Document doc = new Document();
        doc.setTenantId(tenantId);
        doc.setName(baseName);
        doc.setFileType(fileType);
        doc.setFilePath(target.toAbsolutePath().toString());
        doc.setCategory(category == null || category.isBlank() ? "未分类" : category);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setIndexStatus(DocumentIndexStatus.UPLOADED);
        doc.setIndexRetryCount(0);
        Document saved = documentRepository.save(doc);
        Long id = saved.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentIndexQueueService.enqueue(id);
                }
            });
        } else {
            documentIndexQueueService.enqueue(id);
        }
        return saved;
    }

    public List<Document> list(Long tenantId) {
        return documentRepository.findByTenantIdOrderByUploadedAtDesc(tenantId);
    }

    public Document getDocumentForDownload(Long id, Long tenantId) {
        Document doc = documentRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new RuntimeException("文档不存在"));
        if (!Files.exists(Paths.get(doc.getFilePath()))) {
            throw new RuntimeException("文件不存在");
        }
        return doc;
    }

    public void delete(Long id, Long tenantId) throws IOException {
        Document doc = documentRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new RuntimeException("文档不存在"));
        try {
            pythonApiClient.deleteDocumentIndex(tenantId, id);
        } catch (Exception ignored) {
        }
        Files.deleteIfExists(Paths.get(doc.getFilePath()));
        documentRepository.deleteById(id);
    }

    @Transactional
    public Document retryIndex(Long id, Long tenantId) {
        Document doc = documentRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new RuntimeException("文档不存在"));
        doc.setIndexStatus(DocumentIndexStatus.UPLOADED);
        doc.setIndexError(null);
        doc.setIndexRetryCount(0);
        doc.setLastIndexAttemptAt(null);
        Document saved = documentRepository.save(doc);
        Long sid = saved.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentIndexQueueService.enqueue(sid);
                }
            });
        } else {
            documentIndexQueueService.enqueue(sid);
        }
        return saved;
    }

    @Transactional
    public void rebuildKb(Long tenantId) {
        if (appProperties.getPythonIndexRebuildUrl() == null || appProperties.getPythonIndexRebuildUrl().isBlank()) {
            throw new RuntimeException("未配置重建索引地址");
        }
        List<Document> all = documentRepository.findByTenantIdOrderByUploadedAtDesc(tenantId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Document d : all) {
            Map<String, Object> one = new HashMap<>();
            one.put("doc_id", d.getId());
            one.put("file_path", d.getFilePath());
            items.add(one);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("tenant_id", tenantId);
        body.put("items", items);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = pythonApiClient.rebuild(entity);
        Map<String, Object> m = resp.getBody();
        if (!resp.getStatusCode().is2xxSuccessful() || m == null || !Boolean.TRUE.equals(m.get("ok"))) {
            throw new RuntimeException("重建索引失败");
        }
        if (all.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Document d : all) {
            d.setIndexStatus(DocumentIndexStatus.INDEXED);
            d.setIndexError(null);
            d.setIndexRetryCount(0);
            d.setLastIndexedAt(now);
            documentRepository.save(d);
        }
    }
}
