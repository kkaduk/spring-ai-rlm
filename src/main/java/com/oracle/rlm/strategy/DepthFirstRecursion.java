package com.oracle.rlm.strategy;

import com.oracle.rlm.model.RecursionStep;
import com.oracle.rlm.service.RecursiveThinkingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("depthFirstRecursion")
@RequiredArgsConstructor
public class DepthFirstRecursion implements RecursionStrategy {
    
    private final RecursiveThinkingService recursiveThinkingService;
    
    @Override
    public RecursionStep execute(String problem, String context, int maxDepth, int maxBranching) {
        // Depth-first naturally follows the recursive approach
        return recursiveThinkingService.solveRecursively(problem, context, 0, maxDepth, maxBranching, null);
    }
}