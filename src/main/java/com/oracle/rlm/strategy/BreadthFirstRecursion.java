package com.oracle.rlm.strategy;

import com.oracle.rlm.model.RecursionStep;
import com.oracle.rlm.service.RecursiveThinkingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("breadthFirstRecursion")
@RequiredArgsConstructor
public class BreadthFirstRecursion implements RecursionStrategy {
    
    private final RecursiveThinkingService recursiveThinkingService;
    
    @Override
    public RecursionStep execute(String problem, String context, int maxDepth, int maxBranching) {
        // For breadth-first, we still use the recursive service but could optimize traversal
        // This is a simplified version - in practice, you might want to modify the approach
        return recursiveThinkingService.solveRecursively(problem, context, 0, maxDepth, maxBranching, null);
    }
}