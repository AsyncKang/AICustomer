package com.nau.animalhospital.cs.service;

import com.nau.animalhospital.cs.config.AppProperties;
import com.nau.animalhospital.cs.dao.DocumentRepository;
import com.nau.animalhospital.cs.entity.Document;
import com.nau.animalhospital.cs.entity.DocumentIndexStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentIndexRetryScheduler {
    private final DocumentRepository documentRepository;
    private final DocumentIndexQueueService queueService;
    private final AppProperties appProperties;

    @Scheduled(fixedDelay = 120000)
    public void retryFailed() {
        int max = appProperties.getIndex().getMaxRetryCount();
        int interval = appProperties.getIndex().getRetryIntervalMinutes();
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(interval);
        List<Document> failed = documentRepository.findByIndexStatus(DocumentIndexStatus.FAILED);
        for (Document d : failed) {
            if (d.getIndexRetryCount() >= max) {
                continue;
            }
            if (d.getLastIndexAttemptAt() != null && d.getLastIndexAttemptAt().isAfter(threshold)) {
                continue;
            }
            queueService.enqueue(d.getId());
        }
    }
}
