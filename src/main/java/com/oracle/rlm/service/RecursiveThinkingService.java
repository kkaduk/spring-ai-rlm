package com.oracle.rlm.service;

import com.oracle.rlm.config.RlmConfig;
import com.oracle.rlm.model.RecursionStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecursiveThinkingService {

    private final ChatClient.Builder chatClientBuilder;
    private volatile ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final RlmConfig rlmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecursionStep solveRecursively(String problem, String context, int currentDepth,
            int maxDepth, int maxBranching, String parentStepId) {
        long startTime = System.currentTimeMillis();
        String stepId = UUID.randomUUID().toString();

        if (this.chatClient == null) {
            synchronized (this) {
                if (this.chatClient == null) {
                    this.chatClient = chatClientBuilder
                            .defaultSystem("")
                            .build();
                }
            }
        }

        log.info("Processing at depth {}: {}", currentDepth, problem.substring(0, Math.min(50, problem.length())));

        RecursionStep step = RecursionStep.builder()
                .stepId(stepId)
                .depth(currentDepth)
                .problem(problem)
                .parentStepId(parentStepId)
                .build();

        try {
            // Check if we've reached maximum depth or if problem is simple enough
            if (currentDepth >= maxDepth || isBaseProblem(problem)) {
                // Base case: solve directly
                return solveDirectly(step, problem, context, startTime);
            }

            // Recursive case: decompose the problem
            DecompositionResult decomposition = decomposeProblem(problem, context, maxBranching);

            if (!decomposition.needsDecomposition) {
                // Model determined this is a base case
                return solveDirectly(step, problem, context, startTime);
            }

            step.setAction("decompose");
            step.setReasoning(decomposition.reasoning);

            // Recursively solve sub-problems
            List<RecursionStep> subSteps = new ArrayList<>();
            List<String> solutions = new ArrayList<>();

            for (String subProblem : decomposition.subProblems) {
                RecursionStep subStep = solveRecursively(
                        subProblem,
                        context,
                        currentDepth + 1,
                        maxDepth,
                        maxBranching,
                        stepId);
                subSteps.add(subStep);
                solutions.add(subStep.getResult());
            }

            step.setSubSteps(subSteps);

            // Aggregate solutions
            String aggregatedSolution = aggregateSolutions(
                    problem,
                    decomposition.subProblems,
                    solutions,
                    context);

            step.setResult(aggregatedSolution);
            step.setDurationMs(System.currentTimeMillis() - startTime);

            return step;

        } catch (Exception e) {
            log.error("Error in recursive solving at depth {}: {}", currentDepth, e.getMessage());
            step.setAction("error");
            step.setResult("Error: " + e.getMessage());
            step.setDurationMs(System.currentTimeMillis() - startTime);
            return step;
        }
    }

    private RecursionStep solveDirectly(RecursionStep step, String problem, String context, long startTime) {
        log.info("Solving directly: {}", problem.substring(0, Math.min(50, problem.length())));

        step.setAction("solve");

        String prompt = promptTemplateService.createSolvePrompt(problem, context);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(rlmConfig.getSolvingTemperature())
                .build();

        String response = chatClient.prompt()
                .user(prompt)
                .options(options)
                .call()
                .content();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            String solution = jsonNode.get("solution").asText();
            String reasoning = jsonNode.has("reasoning") ? jsonNode.get("reasoning").asText() : "";

            step.setReasoning(reasoning);
            step.setResult(solution);
        } catch (Exception e) {
            log.warn("Failed to parse JSON response, using raw response");
            step.setResult(response);
        }

        step.setDurationMs(System.currentTimeMillis() - startTime);
        return step;
    }

    private DecompositionResult decomposeProblem(String problem, String context, int maxBranching) {
        String prompt = promptTemplateService.createDecomposePrompt(problem, context, maxBranching);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(rlmConfig.getDecompositionTemperature())
                .build();

        String response = chatClient.prompt()
                .user(prompt)
                .options(options)
                .call()
                .content();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);

            DecompositionResult result = new DecompositionResult();
            result.needsDecomposition = jsonNode.get("needsDecomposition").asBoolean();
            result.reasoning = jsonNode.get("reasoning").asText();

            if (result.needsDecomposition && jsonNode.has("subProblems")) {
                JsonNode subProblemsNode = jsonNode.get("subProblems");
                result.subProblems = new ArrayList<>();
                subProblemsNode.forEach(node -> result.subProblems.add(node.asText()));
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to parse decomposition response: {}", e.getMessage());
            DecompositionResult result = new DecompositionResult();
            result.needsDecomposition = false;
            result.reasoning = "Failed to decompose, solving directly";
            return result;
        }
    }

    private String aggregateSolutions(String originalProblem, List<String> subProblems,
            List<String> solutions, String context) {
        String prompt = promptTemplateService.createAggregatePrompt(
                originalProblem,
                subProblems,
                solutions,
                context);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(rlmConfig.getAggregationTemperature())
                .build();

        String response = chatClient.prompt()
                .user(prompt)
                .options(options)
                .call()
                .content();

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("finalAnswer").asText();
        } catch (Exception e) {
            log.warn("Failed to parse aggregation JSON, using raw response");
            return response;
        }
    }

    private boolean isBaseProblem(String problem) {
        // Simple heuristic: if problem is very short, it's likely a base case
        return problem.length() < 100;
    }

    @Cacheable(value = "decompositions", key = "#problem")
    public DecompositionResult decomposeProblemCached(String problem, String context, int maxBranching) {
        return decomposeProblem(problem, context, maxBranching);
    }

    private static class DecompositionResult {
        boolean needsDecomposition;
        String reasoning;
        List<String> subProblems;
    }
}
