package com.oracle.rlm.core.impl;

import com.oracle.rlm.config.RlmConfig;
import com.oracle.rlm.core.RlmClient;
import com.oracle.rlm.core.RlmCompletionRequest;
import com.oracle.rlm.core.RlmCompletionResult;
import com.oracle.rlm.core.RlmEnvironmentStore;
import com.oracle.rlm.model.RecursionStep;
import com.oracle.rlm.model.ThoughtProcess;
import com.oracle.rlm.service.RecursiveThinkingService;
import com.oracle.rlm.strategy.RecursionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultRlmClient implements RlmClient {

    private final RecursiveThinkingService recursiveThinkingService;
    private final RlmConfig rlmConfig;
    private final Map<String, RecursionStrategy> recursionStrategies;
    private final RlmEnvironmentStore environmentStore;

    @Override
    public RlmCompletionResult completion(RlmCompletionRequest request) {
        Instant start = Instant.now();

        String strategyName = request.getStrategy() != null
                ? request.getStrategy()
                : "depth-first";
        RecursionStrategy strategy = resolveStrategy(strategyName);

        int maxDepth = Optional.ofNullable(request.getMaxDepth())
                .orElse(rlmConfig.getMaxDepth());
        int maxBranching = Optional.ofNullable(request.getMaxBranching())
                .orElse(rlmConfig.getMaxBranching());

        // Construct context: inlineContext + environment “view”
        String combinedContext = buildContext(request);

        RecursionStep root = strategy.execute(
                request.getQuery(),
                combinedContext,
                maxDepth,
                maxBranching
        );

        List<ThoughtProcess> thoughts = request.isVerbose()
                ? extractThoughtProcesses(root)
                : Collections.emptyList();

        Duration processingTime = Duration.between(start, Instant.now());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("strategy", strategyName);
        metadata.put("maxDepth", maxDepth);
        metadata.put("maxBranching", maxBranching);
        metadata.put("environmentId", request.getEnvironmentId());

        return RlmCompletionResult.builder()
                .finalAnswer(root.getResult())
                .rawOutput(null) // you can pass raw LLM output if you capture it
                .totalSteps(countTotalSteps(root))
                .maxDepthReached(findMaxDepth(root))
                .processingTime(processingTime)
                .startedAt(start)
                .strategy(strategyName)
                .thoughtProcesses(thoughts)
                .metadata(metadata)
                .build();
    }

    private RecursionStrategy resolveStrategy(String strategyName) {
        String beanName;
        if (strategyName == null || strategyName.isBlank()) {
            beanName = "depthFirstRecursion";
        } else {
            switch (strategyName.toLowerCase()) {
                case "depth-first":
                case "depthfirst":
                case "depth_first":
                    beanName = "depthFirstRecursion";
                    break;
                case "breadth-first":
                case "breadthfirst":
                case "breadth_first":
                    beanName = "breadthFirstRecursion";
                    break;
                default:
                    beanName = strategyName + "Recursion";
            }
        }

        RecursionStrategy strategy = recursionStrategies.get(beanName);
        if (strategy == null) {
            log.warn("Strategy {} not found, falling back to depth-first", strategyName);
            strategy = recursionStrategies.get("depthFirstRecursion");
        }
        return strategy;
    }

    private String buildContext(RlmCompletionRequest request) {
        StringBuilder ctx = new StringBuilder();

        if (request.getInlineContext() != null && !request.getInlineContext().isBlank()) {
            ctx.append("INLINE CONTEXT:\n")
               .append(request.getInlineContext())
               .append("\n\n");
        }

        if (request.getEnvironmentId() != null) {
            environmentStore.getEnvironment(request.getEnvironmentId())
                    .ifPresent(env -> {
                        ctx.append("ENVIRONMENT LABEL: ").append(env.getLabel()).append("\n");
                        // You can design a smarter dump here:
                        ctx.append("ENVIRONMENT SUMMARY (search(\"*\")):\n")
                           .append(env.search("")); // naive: return some summary
                    });
        }

        return ctx.length() > 0 ? ctx.toString() : null;
    }

    private int countTotalSteps(RecursionStep step) {
        int count = 1;
        for (RecursionStep sub : step.getSubSteps()) {
            count += countTotalSteps(sub);
        }
        return count;
    }

    private int findMaxDepth(RecursionStep step) {
        int max = step.getDepth();
        for (RecursionStep sub : step.getSubSteps()) {
            max = Math.max(max, findMaxDepth(sub));
        }
        return max;
    }

    private List<ThoughtProcess> extractThoughtProcesses(RecursionStep root) {
        List<ThoughtProcess> processes = new ArrayList<>();
        extractRecursive(root, processes);
        return processes;
    }

    private void extractRecursive(RecursionStep step, List<ThoughtProcess> processes) {
        ThoughtProcess process = ThoughtProcess.builder()
                .level(step.getDepth())
                .description(step.getProblem())
                .build();

        if ("decompose".equals(step.getAction())) {
            step.getSubSteps().forEach(sub -> {
                process.getSubProblems().add(sub.getProblem());
                process.getSolutions().add(sub.getResult());
            });
            process.setSynthesis(step.getResult());
        } else if ("solve".equals(step.getAction())) {
            process.setSynthesis(step.getResult());
        }

        processes.add(process);

        step.getSubSteps().forEach(sub -> extractRecursive(sub, processes));
    }
}
