package com.docint.query.service;

import com.docint.common.entity.Message;
import com.docint.query.llm.ResilientRagLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewritingService {

    private final ResilientRagLlmClient llmClient;

    /**
     * Rewrites a follow-up query in the context of recent chat history into a
     * standalone search query for hybrid retrieval.
     */
    public String rewriteQuery(List<Message> history, String currentQuestion) {
        if (history == null || history.isEmpty() || currentQuestion == null || currentQuestion.isBlank()) {
            return currentQuestion;
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("You are an expert search query rewriter for a document retrieval engine. ")
              .append("Given the recent chat conversation history and the user's latest question, rewrite the latest question ")
              .append("into a standalone, unambiguous search query containing all necessary context (entities, dates, subject matter). ")
              .append("If the latest query is already completely standalone, return it as-is.\n\n")
              .append("RULES:\n")
              .append("- Do NOT answer the question.\n")
              .append("- Do NOT add commentary, explanations, or quotes.\n")
              .append("- Output ONLY the rewritten search query.\n\n")
              .append("--- CHAT HISTORY ---\n");

            for (Message msg : history) {
                sb.append(msg.getRole().name()).append(": ").append(msg.getContent()).append("\n");
            }

            sb.append("--- END CHAT HISTORY ---\n\n")
              .append("Latest User Question: ").append(currentQuestion.trim()).append("\n\n")
              .append("Standalone Search Query:");

            var response = llmClient.generateAnswer(sb.toString());
            String rewritten = response.answer();

            if (rewritten != null) {
                rewritten = rewritten.trim();
                // Clean enclosing quotes if any
                if (rewritten.startsWith("\"") && rewritten.endsWith("\"") && rewritten.length() > 1) {
                    rewritten = rewritten.substring(1, rewritten.length() - 1).trim();
                }
                if (!rewritten.isBlank() && rewritten.length() < 1000) {
                    log.info("Rewrote query: '{}' -> '{}'", currentQuestion, rewritten);
                    return rewritten;
                }
            }
        } catch (Exception e) {
            log.warn("Query rewriting failed or timed out ({}). Using original query: '{}'",
                    e.getMessage(), currentQuestion);
        }

        return currentQuestion;
    }
}
