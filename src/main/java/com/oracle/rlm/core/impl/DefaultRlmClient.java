package com.oracle.rlm.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.rlm.config.RlmConfig;
import com.oracle.rlm.core.*;
import com.oracle.rlm.service.RlmPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultRlmClient implements RlmClient {

    private final ChatClient.Builder chatClientBuilder;
    private volatile ChatClient chatClient;
    private final RlmPromptService promptService;
    private final RlmEnvironmentStore environmentStore;
    private final RlmConfig rlmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RlmCompletionResult completion(RlmCompletionRequest request) {
        Instant start = Instant.now();
        
        if (this.chatClient == null) {
            synchronized (this) {
                if (this.chatClient == null) {
                    this.chatClient = chatClientBuilder
                            .defaultSystem(promptService.createSystemPrompt())
                            .build();
                }
            }
        }

        // Get or create environment
        RlmEnvironment env = getOrCreateEnvironment(request);
        
        // Setup initial context
        if (request.getInlineContext() != null) {
            env.putContextChunk("initial_context", request.getInlineContext());
        }

        int maxSteps = request.getMaxDepth() != null ? request.getMaxDepth() * 10 : 30;
        int step = 0;
        boolean finished = false;
        String finalAnswer = null;

        try {
            while (!finished && step < maxSteps) {
                step++;
                log.info("RLM Step {}/{}", step, maxSteps);

                // Build prompt with history
                String userPrompt = promptService.createUserPrompt(
                        request.getQuery(),
                        env.getHistory(),
                        env.getEnvironmentInfo()
                );

                // Get model response
                String response = chatClient.prompt()
                        .user(userPrompt)
                        .call()
                        .content();

                // Parse response
                StepResponse stepResponse = parseStepResponse(response);

                if (stepResponse.finished) {
                    finished = true;
                    finalAnswer = stepResponse.answer;
                    log.info("RLM finished at step {}", step);
                    break;
                }

                // Execute tool
                ToolCall toolCall = ToolCall.builder()
                        .toolName(stepResponse.tool)
                        .code(stepResponse.code)
                        .reasoning(stepResponse.thought)
                        .build();

                ToolResult result = executeTool(env, toolCall);

                // Record observation
                ActionObservation observation = ActionObservation.builder()
                        .step(step)
                        .thought(stepResponse.thought)
                        .action(toolCall)
                        .observation(result)
                        .timestamp(System.currentTimeMillis())
                        .build();

                env.addObservation(observation);

                if (!result.isSuccess()) {
                    log.warn("Tool execution failed at step {}: {}", step, result.getError());
                }
            }

            if (!finished) {
                log.warn("RLM reached max steps ({}) without finishing", maxSteps);
                finalAnswer = "Maximum steps reached without complete solution. " +
                             "Last observations: " + summarizeHistory(env.getHistory(), 3);
            }

            Duration processingTime = Duration.between(start, Instant.now());

            return RlmCompletionResult.builder()
                    .finalAnswer(finalAnswer)
                    .totalSteps(step)
                    .maxDepthReached(step)
                    .processingTime(processingTime)
                    .startedAt(start)
                    .strategy("rlm-action-loop")
                    .thoughtProcesses(request.isVerbose() ? 
                        convertToThoughtProcesses(env.getHistory()) : null)
                    .metadata(Map.of(
                            "environmentId", env.getId(),
                            "totalObservations", env.getHistory().size(),
                            "workingDir", env.getCurrentWorkingDirectory()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("RLM execution failed", e);
            throw new RuntimeException("RLM execution failed: " + e.getMessage(), e);
        }
    }

    private RlmEnvironment getOrCreateEnvironment(RlmCompletionRequest request) {
        if (request.getEnvironmentId() != null) {
            return environmentStore.getEnvironment(request.getEnvironmentId())
                    .orElseGet(() -> environmentStore.createEnvironment("auto-created"));
        }
        return environmentStore.createEnvironment("request-" + UUID.randomUUID());
    }

    private StepResponse parseStepResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            
            StepResponse sr = new StepResponse();
            sr.thought = node.has("thought") ? node.get("thought").asText() : "";
            sr.finished = node.has("finished") && node.get("finished").asBoolean();
            
            if (sr.finished) {
                sr.answer = node.has("answer") ? node.get("answer").asText() : "";
            } else {
                sr.tool = node.has("tool") ? node.get("tool").asText() : "python";
                sr.code = node.has("code") ? node.get("code").asText() : "";
            }
            
            return sr;
        } catch (Exception e) {
            log.error("Failed to parse step response: {}", response, e);
            // Fallback
            StepResponse sr = new StepResponse();
            sr.thought = "Parse error";
            sr.finished = true;
            sr.answer = response;
            return sr;
        }
    }

    private ToolResult executeTool(RlmEnvironment env, ToolCall toolCall) {
        return switch (toolCall.getToolName().toLowerCase()) {
            case "python" -> env.executePython(toolCall.getCode());
            case "bash" -> env.executeBash(toolCall.getCode());
            case "write_file" -> {
                String[] parts = toolCall.getCode().split("\n", 2);
                yield env.writeFile(parts[0], parts.length > 1 ? parts[1] : "");
            }
            case "read_file" -> env.readFile(toolCall.getCode());
            case "search" -> ToolResult.builder()
                    .success(true)
                    .output(env.search(toolCall.getCode()))
                    .build();
            default -> ToolResult.builder()
                    .success(false)
                    .error("Unknown tool: " + toolCall.getToolName())
                    .build();
        };
    }

    private String summarizeHistory(List<ActionObservation> history, int last) {
        return history.stream()
                .skip(Math.max(0, history.size() - last))
                .map(obs -> String.format("Step %d: %s -> %s",
                        obs.getStep(),
                        obs.getAction().getToolName(),
                        obs.getObservation().isSuccess() ? "success" : "failed"))
                .reduce("", (a, b) -> a + "\n" + b);
    }

    private List<com.oracle.rlm.model.ThoughtProcess> convertToThoughtProcesses(
            List<ActionObservation> history) {
        // Convert observations to ThoughtProcess for compatibility
        return history.stream()
                .map(obs -> com.oracle.rlm.model.ThoughtProcess.builder()
                        .level(obs.getStep())
                        .description(obs.getThought())
                        .synthesis(obs.getObservation().getOutput())
                        .build())
                .toList();
    }

    private static class StepResponse {
        String thought;
        String tool;
        String code;
        boolean finished;
        String answer;
    }
}