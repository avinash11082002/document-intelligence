package com.docint.worker.extractor;

import java.io.IOException;

/**
 * Extraction result with both text content and metadata (page count).
 */
public interface TextExtractor {

    ExtractionResult extractText(String filePath) throws IOException;

    record ExtractionResult(String text, int pageCount) {}
}
