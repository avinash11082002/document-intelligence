package com.docint.common.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "message content must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String content
) {}
