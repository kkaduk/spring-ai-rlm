package com.oracle.rlm.service;

import com.oracle.rlm.config.RlmConfig;
import com.oracle.rlm.model.*;
import com.oracle.rlm.core.RlmClient;
import com.oracle.rlm.core.RlmCompletionRequest;
import com.oracle.rlm.core.RlmCompletionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RlmService {
    
    private final RlmClient rlmClient;
    private final RlmConfig rlmConfig;
    
    public RlmResponse processRequest(RlmRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Processing RLM request via core client: {}", request.getProblem());

        int maxDepth = request.getMaxDepth() != null ? request.getMaxDepth() : rlmConfig.getMaxDepth();
        int maxBranching = request.getMaxBranching() != null ? request.getMaxBranching() : rlmConfig.getMaxBranching();

        try {
            RlmCompletionRequest coreRequest = RlmCompletionRequest.builder()
                    .query(request.getProblem())
                    .inlineContext(request.getContext())
                    .maxDepth(maxDepth)
                    .maxBranching(maxBranching)
                    .strategy(request.getStrategy())
                    .verbose(Boolean.TRUE.equals(request.getVerbose()))
                    .build();

            RlmCompletionResult result = rlmClient.completion(coreRequest);

            RlmResponse response = RlmResponse.builder()
                    .problem(request.getProblem())
                    .finalAnswer(result.getFinalAnswer())
                    .thoughtProcesses(result.getThoughtProcesses())
                    .totalSteps(result.getTotalSteps())
                    .maxDepthReached(result.getMaxDepthReached())
                    .processingTimeMs(result.getProcessingTime() != null
                            ? result.getProcessingTime().toMillis()
                            : (System.currentTimeMillis() - startTime))
                    .strategy(result.getStrategy())
                    .build();

            log.info("Completed RLM request via core in {}ms", response.getProcessingTimeMs());
            return response;

        } catch (Exception e) {
            log.error("Error processing RLM request via core: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process RLM request: " + e.getMessage(), e);
        }
    }
    
    
    
    
    
}
