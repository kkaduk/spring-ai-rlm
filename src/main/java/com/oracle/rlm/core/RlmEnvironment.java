package com.oracle.rlm.core;

import java.util.List;

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
     * Store the full context in the environment (e.g., as a file for REPL access).
     */
    void setFullContext(String context);

    /**
     * Retrieve the full context, if available.
     */
    String getFullContext();

    /**
     * Size of the full context in characters, or 0 if none.
     */
    long getContextSize();

    /**
     * Optional search API – lets the model “ask” to search the environment.
     * In real RLM, this would be invoked via tool-calls / code in a REPL.
     */
    // default String search(String query) {
    //     // no-op or simple implementation; can be enhanced later
    //     return "";
    // }

    // NEW: Tool execution capabilities
    ToolResult executePython(String code);
    ToolResult executeBash(String command);
    ToolResult writeFile(String filename, String content);
    ToolResult readFile(String filename);
    String search(String query);
    
    // NEW: Observation history for the model
    List<ActionObservation> getHistory();
    void addObservation(ActionObservation observation);
    
    // NEW: Environment state
    String getCurrentWorkingDirectory();
    List<String> listFiles();
    String getEnvironmentInfo();
}
