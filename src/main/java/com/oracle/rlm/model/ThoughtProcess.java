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
public class ThoughtProcess {
    
    private Integer level;
    
    private String description;
    
    @Builder.Default
    private List<String> subProblems = new ArrayList<>();
    
    @Builder.Default
    private List<String> solutions = new ArrayList<>();
    
    private String synthesis;
}