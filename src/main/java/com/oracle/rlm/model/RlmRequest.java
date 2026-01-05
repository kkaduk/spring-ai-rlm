package com.oracle.rlm.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RlmRequest {
    
    @NotBlank(message = "Problem statement cannot be blank")
    private String problem;
    
    @Min(value = 1, message = "Max depth must be at least 1")
    @Max(value = 5, message = "Max depth cannot exceed 5")
    @Builder.Default
    private Integer maxDepth = 3;
    
    @Min(value = 1, message = "Max branching must be at least 1")
    @Max(value = 5, message = "Max branching cannot exceed 5")
    @Builder.Default
    private Integer maxBranching = 3;
    
    @Builder.Default
    private String strategy = "depth-first"; // or "breadth-first"
    
    @Builder.Default
    private Boolean verbose = false;
    
    private String context; // Additional context for the problem
}