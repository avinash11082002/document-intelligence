package com.docint.worker.extractor.impl;

import com.docint.worker.extractor.TextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.Office;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class TikaTextExtractor implements TextExtractor {

    private final AutoDetectParser parser = new AutoDetectParser();

    @Override
    public ExtractionResult extractText(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found on disk: " + filePath);
        }

        try (InputStream is = Files.newInputStream(path)) {
            // -1 = no character limit on body text
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(is, handler, metadata, context);

            String text = handler.toString();
            int pageCount = extractPageCount(metadata);

            log.info("Extracted {} chars, {} pages from {}", text.length(), pageCount, path.getFileName());
            return new ExtractionResult(text, pageCount);

        } catch (TikaException | SAXException e) {
            throw new IOException("Tika parsing failed for " + filePath, e);
        }
    }

    private int extractPageCount(Metadata metadata) {
        String[] pageKeys = {
                "xmpTPg:NPages",
                "Page-Count",
                "meta:page-count",
                "pdf:unmappedPageCount"
        };
        for (String key : pageKeys) {
            String val = metadata.get(key);
            if (val != null && !val.isBlank()) {
                try {
                    int p = Integer.parseInt(val.trim());
                    if (p > 0) return p;
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0; // caller defaults to 1 when 0
    }
}
