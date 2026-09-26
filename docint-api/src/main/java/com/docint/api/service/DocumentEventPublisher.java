package com.docint.api.service;

import com.docint.common.entity.Document;

/**
 * Event publisher abstraction adhering to DIP & SRP.
 */
public interface DocumentEventPublisher {

    void publishUploadedEvent(Document document);
}
