package com.oracle.rlm.core;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.Map;

@Data
@Builder
public class RlmCompletionRequest {

    /**
     * User query / instruction – analogous to a normal LLM prompt.
     */
    private String query;

    /**
     * Optional environment id that holds large context
     * (documents, conversation history, etc.).
     */
    private String environmentId;

    /**
     * Optional extra context (small, prompt-sized).
     */
    private String inlineContext;

    /**
     * Maximum recursion depth for this call.
     * If null, use default from RlmConfig.
     */
    private Integer maxDepth;

    /**
     * Maximum branching factor for decomposition.
     */
    private Integer maxBranching;

    /**
     * Which recursion strategy to use (depth-first, breadth-first, etc.).
     */
    @Builder.Default
    private String strategy = "depth-first";

    /**
     * Whether to include the full reasoning / tree in the result.
     */
    @Builder.Default
    private boolean verbose = false;

    /**
     * Optional per-call timeout, beyond the global config.
     */
    private Duration timeout;

    /**
     * Optional model / backend hints (e.g., "openai:gpt-4o", "anthropic:claude-3").
     */
    private Map<String, Object> backendHints;
}
