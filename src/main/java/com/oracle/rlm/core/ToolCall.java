package com.oracle.rlm.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String toolName;      // "python", "bash", "search", "write_file"
    private String code;          // The actual code/command to execute
    private String reasoning;     // Why the model wants to execute this
}

