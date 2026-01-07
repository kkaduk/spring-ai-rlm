package com.oracle.rlm.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionObservation {
    private int step;
    private String thought;
    private ToolCall action;
    private ToolResult observation;
    private long timestamp;
}
