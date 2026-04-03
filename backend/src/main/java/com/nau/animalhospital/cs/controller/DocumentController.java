package com.nau.animalhospital.cs.controller;

import com.nau.animalhospital.cs.entity.Document;
import com.nau.animalhospital.cs.security.JwtUserPrincipal;
import com.nau.animalhospital.cs.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public Document upload(@AuthenticationPrincipal JwtUserPrincipal principal,
                           @RequestParam("file") MultipartFile file,
                           @RequestParam(value = "category", required = false) String category,
                           @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) throws IOException {
        return documentService.upload(file, category, principal.tenantId(), overwrite);
    }

    @GetMapping
    public List<Document> list(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return documentService.list(principal.tenantId());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable Long id) {
        Document doc = documentService.getDocumentForDownload(id, principal.tenantId());
        Resource resource = new FileSystemResource(Paths.get(doc.getFilePath()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(doc.getName(), StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable Long id) throws IOException {
        documentService.delete(id, principal.tenantId());
    }

    @PostMapping("/{id}/retry-index")
    @PreAuthorize("hasRole('ADMIN')")
    public Document retryIndex(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable Long id) {
        return documentService.retryIndex(id, principal.tenantId());
    }

    @PostMapping("/rebuild-index")
    @PreAuthorize("hasRole('ADMIN')")
    public void rebuildIndex(@AuthenticationPrincipal JwtUserPrincipal principal) {
        documentService.rebuildKb(principal.tenantId());
    }
}
