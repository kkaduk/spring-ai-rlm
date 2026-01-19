package com.oracle.rlm.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private String output;        // stdout/result
    private String error;         // stderr/error message
    private long executionTimeMs;
}