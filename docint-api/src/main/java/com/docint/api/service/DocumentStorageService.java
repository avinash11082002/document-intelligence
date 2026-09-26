package com.docint.api.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Single-responsibility abstraction for storing raw document files.
 * Adheres to DIP (Dependency Inversion) and OCP (Open/Closed for S3/Blob storage in future).
 */
public interface DocumentStorageService {

    String store(UUID documentId, MultipartFile file) throws IOException;

    void delete(UUID documentId) throws IOException;
}
