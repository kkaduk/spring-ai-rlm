package com.oracle.rlm.core;

import com.oracle.rlm.model.ThoughtProcess;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RlmCompletionResult {

    /**
     * Final answer returned by the recursive process.
     */
    private String finalAnswer;

    /**
     * Optional “raw” model output, if you keep it.
     */
    private String rawOutput;

    /**
     * Total number of recursive steps taken.
     */
    private Integer totalSteps;

    /**
     * Maximum recursion depth actually reached.
     */
    private Integer maxDepthReached;

    /**
     * Wall-clock processing time.
     */
    private Duration processingTime;

    /**
     * Timestamp for observability.
     */
    private Instant startedAt;

    /**
     * Strategy used ("depth-first", "breadth-first", etc.).
     */
    private String strategy;

    /**
     * Optional thought process, exposed when verbose=true.
     * Reuses your existing ThoughtProcess model.
     */
    private List<ThoughtProcess> thoughtProcesses;

    /**
     * Metadata for observability (chosen backend, cache hits, etc.).
     */
    private Map<String, Object> metadata;
}
