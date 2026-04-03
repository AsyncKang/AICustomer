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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentIndexProcessor {
    private final DocumentRepository documentRepository;
    private final AppProperties appProperties;
    private final PythonApiClient pythonApiClient;

    @Transactional
    public void process(Long docId) {
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) {
            return;
        }
        if (doc.getIndexStatus() == DocumentIndexStatus.INDEXED) {
            return;
        }
        if (doc.getIndexRetryCount() >= appProperties.getIndex().getMaxRetryCount()) {
            return;
        }
        try {
            Path p = Paths.get(doc.getFilePath());
            if (!Files.exists(p) || Files.size(p) == 0) {
                fail(doc, "文件不存在或为空");
                return;
            }
            doc.setIndexStatus(DocumentIndexStatus.PARSED);
            doc.setIndexError(null);
            documentRepository.save(doc);
            String url = appProperties.getPythonIncrementalIndexUrl();
            if (url == null || url.isBlank()) {
                fail(doc, "未配置索引地址");
                return;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("tenant_id", doc.getTenantId() != null ? doc.getTenantId() : 1L);
            body.put("doc_id", doc.getId());
            body.put("file_path", doc.getFilePath());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            Exception last = null;
            int attempts = appProperties.getIndex().getHttpAttempts();
            for (int i = 1; i <= attempts; i++) {
                try {
                    ResponseEntity<Map<String, Object>> resp = pythonApiClient.indexIncremental(entity);
                    Map<String, Object> m = resp.getBody();
                    if (resp.getStatusCode().is2xxSuccessful() && m != null && Boolean.TRUE.equals(m.get("ok"))) {
                        doc.setIndexStatus(DocumentIndexStatus.EMBEDDED);
                        documentRepository.save(doc);
                        doc.setIndexStatus(DocumentIndexStatus.INDEXED);
                        doc.setLastIndexedAt(LocalDateTime.now());
                        doc.setIndexError(null);
                        doc.setIndexRetryCount(0);
                        documentRepository.save(doc);
                        return;
                    }
                    last = new RuntimeException("bad response");
                } catch (Exception e) {
                    last = e;
                    if (i < attempts) {
                        try {
                            Thread.sleep(1000L * i);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
            fail(doc, last != null && last.getMessage() != null ? last.getMessage() : "索引失败");
        } catch (Exception e) {
            Document d = documentRepository.findById(docId).orElse(null);
            if (d != null) {
                String em = e.getMessage();
                fail(d, em != null ? em : e.getClass().getSimpleName());
            }
        }
    }

    private void fail(Document doc, String msg) {
        doc.setIndexStatus(DocumentIndexStatus.FAILED);
        if (msg != null && msg.length() > 1000) {
            doc.setIndexError(msg.substring(0, 1000));
        } else {
            doc.setIndexError(msg);
        }
        doc.setLastIndexAttemptAt(LocalDateTime.now());
        doc.setIndexRetryCount(doc.getIndexRetryCount() + 1);
        documentRepository.save(doc);
    }
}
