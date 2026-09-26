package com.docint.common.dto;

import java.util.UUID;

public record ReplayResponse(
        UUID documentId,
        String status,
        String message
) {}
