package com.oracle.rlm.service;

import com.oracle.rlm.config.RlmConfig;
import com.oracle.rlm.model.*;
import com.oracle.rlm.strategy.RecursionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RlmService {
    
    private final RecursiveThinkingService recursiveThinkingService;
    private final RlmConfig rlmConfig;
    private final Map<String, RecursionStrategy> recursionStrategies;
    
    public RlmResponse processRequest(RlmRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Processing RLM request: {}", request.getProblem());
        
        int maxDepth = request.getMaxDepth() != null ? request.getMaxDepth() : rlmConfig.getMaxDepth();
        int maxBranching = request.getMaxBranching() != null ? request.getMaxBranching() : rlmConfig.getMaxBranching();
        
        try {
            // Get the appropriate recursion strategy
            RecursionStrategy strategy = getStrategy(request.getStrategy());
            
            // Execute recursive solving
            RecursionStep rootStep = recursiveThinkingService.solveRecursively(
                request.getProblem(),
                request.getContext(),
                0,
                maxDepth,
                maxBranching,
                null
            );
            
            // Build response
            RlmResponse response = RlmResponse.builder()
                .problem(request.getProblem())
                .finalAnswer(rootStep.getResult())
                .strategy(request.getStrategy())
                .totalSteps(countTotalSteps(rootStep))
                .maxDepthReached(findMaxDepth(rootStep))
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
            
            // Add thought processes if verbose
            if (Boolean.TRUE.equals(request.getVerbose())) {
                response.setThoughtProcesses(extractThoughtProcesses(rootStep));
            }
            
            log.info("Completed RLM request in {}ms", response.getProcessingTimeMs());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error processing RLM request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process RLM request: " + e.getMessage(), e);
        }
    }
    
    private RecursionStrategy getStrategy(String strategyName) {
        String key = strategyName != null ? strategyName + "Recursion" : "depthFirstRecursion";
        RecursionStrategy strategy = recursionStrategies.get(key);
        if (strategy == null) {
            log.warn("Strategy {} not found, using depth-first", strategyName);
            strategy = recursionStrategies.get("depthFirstRecursion");
        }
        return strategy;
    }
    
    private int countTotalSteps(RecursionStep step) {
        int count = 1;
        for (RecursionStep subStep : step.getSubSteps()) {
            count += countTotalSteps(subStep);
        }
        return count;
    }
    
    private int findMaxDepth(RecursionStep step) {
        int maxDepth = step.getDepth();
        for (RecursionStep subStep : step.getSubSteps()) {
            maxDepth = Math.max(maxDepth, findMaxDepth(subStep));
        }
        return maxDepth;
    }
    
    private List<ThoughtProcess> extractThoughtProcesses(RecursionStep step) {
        List<ThoughtProcess> processes = new ArrayList<>();
        extractThoughtProcessesRecursive(step, processes);
        return processes;
    }
    
    private void extractThoughtProcessesRecursive(RecursionStep step, List<ThoughtProcess> processes) {
        ThoughtProcess process = ThoughtProcess.builder()
            .level(step.getDepth())
            .description(step.getProblem())
            .build();
        
        if ("decompose".equals(step.getAction())) {
            for (RecursionStep subStep : step.getSubSteps()) {
                process.getSubProblems().add(subStep.getProblem());
                process.getSolutions().add(subStep.getResult());
            }
            process.setSynthesis(step.getResult());
        } else if ("solve".equals(step.getAction())) {
            process.setSynthesis(step.getResult());
        }
        
        processes.add(process);
        
        // Recursively process sub-steps
        for (RecursionStep subStep : step.getSubSteps()) {
            extractThoughtProcessesRecursive(subStep, processes);
        }
    }
}