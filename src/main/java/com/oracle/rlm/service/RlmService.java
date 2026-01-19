package com.oracle.rlm.service;

import com.oracle.rlm.config.RlmConfig;
import com.oracle.rlm.core.RlmClient;
import com.oracle.rlm.core.RlmCompletionRequest;
import com.oracle.rlm.core.RlmCompletionResult;
import com.oracle.rlm.model.RlmRequest;
import com.oracle.rlm.model.RlmResponse;
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
        log.info("Processing RLM request: {}", request.getProblem());

        try {
            RlmCompletionRequest coreRequest = RlmCompletionRequest.builder()
                    .query(request.getProblem())
                    .inlineContext(request.getContext())
                    .maxDepth(request.getMaxDepth() != null ? 
                        request.getMaxDepth() : rlmConfig.getMaxDepth())
                    .maxBranching(request.getMaxBranching() != null ?
                        request.getMaxBranching() : rlmConfig.getMaxBranching())
                    .strategy(request.getStrategy())
                    .verbose(Boolean.TRUE.equals(request.getVerbose()))
                    .build();

            RlmCompletionResult result = rlmClient.completion(coreRequest);

            return RlmResponse.builder()
                    .problem(request.getProblem())
                    .finalAnswer(result.getFinalAnswer())
                    .thoughtProcesses(result.getThoughtProcesses())
                    .totalSteps(result.getTotalSteps())
                    .maxDepthReached(result.getMaxDepthReached())
                    .processingTimeMs(result.getProcessingTime().toMillis())
                    .strategy(result.getStrategy())
                    .build();

        } catch (Exception e) {
            log.error("Error processing RLM request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process RLM request: " + e.getMessage(), e);
        }
    }
}
