package com.oracle.rlm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RlmResponse {
    
    private String problem;
    
    private String finalAnswer;
    
    @Builder.Default
    private List<ThoughtProcess> thoughtProcesses = new ArrayList<>();
    
    private Integer totalSteps;
    
    private Integer maxDepthReached;
    
    private Long processingTimeMs;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    private String strategy;
}