package com.oracle.rlm.core;

/**
 * Represents a logical environment in which an RLM call runs.
 * It can hold arbitrarily large context outside the model’s prompt window.
 */
public interface RlmEnvironment {

    /**
     * Unique id of this environment (e.g., UUID).
     */
    String getId();

    /**
     * Human-readable label (for debug / UI).
     */
    String getLabel();

    /**
     * Retrieve a chunk of context by key (e.g. "doc:1234", "section:intro").
     */
    String getContextChunk(String key);

    /**
     * Store / update a chunk of context.
     */
    void putContextChunk(String key, String value);

    /**
     * Optional search API – lets the model “ask” to search the environment.
     * In real RLM, this would be invoked via tool-calls / code in a REPL.
     */
    default String search(String query) {
        // no-op or simple implementation; can be enhanced later
        return "";
    }
}
