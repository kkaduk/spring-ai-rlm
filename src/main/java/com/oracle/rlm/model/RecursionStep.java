package com.oracle.rlm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecursionStep {
    
    private String stepId;
    
    private Integer depth;
    
    private String problem;
    
    private String action; // "decompose", "solve", "aggregate"
    
    private String reasoning;
    
    private String result;
    
    @Builder.Default
    private List<RecursionStep> subSteps = new ArrayList<>();
    
    private String parentStepId;
    
    private Long durationMs;
}