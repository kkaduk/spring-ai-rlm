package com.oracle.rlm.service;

import org.springframework.beans.factory.annotation.Autowired;

import com.oracle.rlm.core.RlmClient;
import com.oracle.rlm.core.RlmCompletionRequest;
import com.oracle.rlm.core.RlmEnvironmentStore;

public class CoreDemoService {
    @Autowired
    private RlmClient rlmClient;

    @Autowired
    private RlmEnvironmentStore environmentStore;

    public void demo() {
        // 1) Create environment and stuff it with big context
        var env = environmentStore.createEnvironment("Long PDF about banking regulations");
        env.putContextChunk("doc:1", "Very long banking regulation text ...");
        env.putContextChunk("doc:2", "Another huge annex ...");

        // 2) Call RLM “completion”
        var result = rlmClient.completion(
                RlmCompletionRequest.builder()
                        .query("Summarize all regulatory constraints that affect mortgage products.")
                        .environmentId(env.getId())
                        .inlineContext("Focus on constraints relevant for loan-to-value and risk scoring.")
                        .maxDepth(3)
                        .maxBranching(3)
                        .strategy("depth-first")
                        .verbose(true)
                        .build());

        System.out.println("FINAL ANSWER: " + result.getFinalAnswer());
        System.out.println("STEPS: " + result.getTotalSteps());
    }

}
