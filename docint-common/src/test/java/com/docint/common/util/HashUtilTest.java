package com.docint.common.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    void sha256_consistent() {
        byte[] bytes = "Sample data for hashing".getBytes();
        assertEquals(HashUtil.sha256(bytes), HashUtil.sha256(bytes));
        assertEquals(64, HashUtil.sha256(bytes).length());
    }

    @Test
    void sha256_stream() throws IOException {
        byte[] bytes = "Stream data for hashing".getBytes();
        String byteHash = HashUtil.sha256(bytes);
        String streamHash = HashUtil.sha256(new ByteArrayInputStream(bytes));
        assertEquals(byteHash, streamHash);
    }

    @Test
    void hashQuestion_normalizesWhitespaceAndCase() {
        String h1 = HashUtil.hashQuestion("What is the invoice total?");
        String h2 = HashUtil.hashQuestion("  what is the invoice total? \n");
        assertEquals(h1, h2);
    }
}
