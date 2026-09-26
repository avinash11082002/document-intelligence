package com.docint.worker.llm;

public interface ChatLlmClient {

    LlmChatResult chat(String prompt);

    record LlmChatResult(String content, String provider) {}
}
