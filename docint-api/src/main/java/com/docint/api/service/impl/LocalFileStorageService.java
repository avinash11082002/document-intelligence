package com.docint.api.service.impl;

import com.docint.api.service.DocumentStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService implements DocumentStorageService {

    private final Path uploadDir;

    public LocalFileStorageService(@Value("${app.upload-dir:/data/uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
        try {
            Files.createDirectories(this.uploadDir);
            log.info("Local storage initialized at: {}", this.uploadDir.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize upload directory: " + uploadDir, e);
        }
    }

    @Override
    public String store(UUID documentId, MultipartFile file) throws IOException {
        Path documentDir = uploadDir.resolve(documentId.toString());
        Files.createDirectories(documentDir);

        String filename = sanitizeFilename(file.getOriginalFilename());
        Path targetPath = documentDir.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = uploadDir.getFileName() + "/" + documentId + "/" + filename;
        log.debug("Stored document file: {} ({} bytes)", relativePath, file.getSize());
        return relativePath;
    }

    @Override
    public void delete(UUID documentId) throws IOException {
        Path documentDir = uploadDir.resolve(documentId.toString());
        if (Files.exists(documentDir)) {
            Files.walk(documentDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
            log.debug("Deleted file storage for document: {}", documentId);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document.bin";
        }
        return Path.of(filename).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
