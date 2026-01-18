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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        int maxDepth = request.getMaxDepth() != null ? request.getMaxDepth() : rlmConfig.getMaxDepth();
        int maxBranching = request.getMaxBranching() != null
                ? request.getMaxBranching()
                : rlmConfig.getMaxBranching();
        
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
        seedEnvironmentContext(env, request.getInlineContext());

        try {
            ExecutionResult execution = runCompletion(request, env, 0, maxDepth, maxBranching);
            Duration processingTime = Duration.between(start, Instant.now());

            return RlmCompletionResult.builder()
                    .finalAnswer(execution.finalAnswer)
                    .totalSteps(execution.totalSteps)
                    .maxDepthReached(execution.maxDepthReached)
                    .processingTime(processingTime)
                    .startedAt(start)
                    .strategy("rlm-recursive-repl")
                    .thoughtProcesses(request.isVerbose()
                        ? convertToThoughtProcesses(env.getHistory()) : null)
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
            JsonNode node = tryExtractJsonNode(response);

            StepResponse sr = new StepResponse();
            sr.thought = node.has("thought") ? node.get("thought").asText() : "";

            // If the model signals finish via tool, infer finished = true even if missing
            boolean finishedFlag = node.has("finished") && node.get("finished").asBoolean();
            if (!finishedFlag && node.has("tool") && "finish".equalsIgnoreCase(node.get("tool").asText())) {
                finishedFlag = true;
            }
            sr.finished = finishedFlag;

            if (sr.finished) {
                sr.answer = node.has("answer") ? node.get("answer").asText() : node.toString();
            } else {
                sr.tool = node.has("tool") ? node.get("tool").asText() : "python";
                sr.code = node.has("code") ? node.get("code").asText() : "";
                if (sr.code == null) sr.code = "";
            }

            return sr;
        } catch (Exception e) {
            // Non-JSON model output is common; handle quietly without stacktrace spam.
            log.warn("Invalid step response (non-JSON). Applying fallback. Snippet: {}",
                    abbreviate(response, 400));

            // Heuristics fallback:
            // 1) If there's a python/bash fenced block, execute it as the chosen tool.
            // 2) Otherwise, nudge the model by executing a harmless echo via bash and continue.
            StepResponse sr = new StepResponse();
            sr.thought = "Model response was not valid JSON. Applying heuristic fallback.";

            Optional<Map.Entry<String, String>> fenced = extractCodeFromFence(response);
            if (fenced.isPresent()) {
                Map.Entry<String, String> entry = fenced.get();
                sr.tool = entry.getKey();
                sr.code = entry.getValue();
                sr.finished = false;
            } else {
                sr.tool = "bash";
                sr.code = "echo 'Formatting error: Respond ONLY with JSON per the schema (no prose, no code fences).'";
                sr.finished = false;
            }
            return sr;
        }
    }

    // Try to extract a JSON object from the model response, supporting:
    // - pure JSON
    // - JSON inside ```json ...``` fences
    // - largest plausible {...} substring
    private JsonNode tryExtractJsonNode(String response) throws Exception {
        String trimmed = response == null ? "" : response.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '{') {
            return objectMapper.readTree(trimmed);
        }

        // Look for ```json ...``` fenced blocks
        int fenceStart = trimmed.indexOf("```");
        while (fenceStart != -1) {
            int fenceEnd = trimmed.indexOf("```", fenceStart + 3);
            if (fenceEnd == -1) break;

            int headerEnd = trimmed.indexOf('\n', fenceStart + 3);
            String header = "";
            int contentStart;
            if (headerEnd != -1 && headerEnd < fenceEnd) {
                header = trimmed.substring(fenceStart + 3, headerEnd).trim().toLowerCase(Locale.ROOT);
                contentStart = headerEnd + 1;
            } else {
                contentStart = fenceStart + 3;
            }
            String code = trimmed.substring(contentStart, fenceEnd).trim();
            if (header.contains("json")) {
                try {
                    return objectMapper.readTree(code);
                } catch (Exception ignore) {
                    // try next fence
                }
            }
            fenceStart = trimmed.indexOf("```", fenceEnd + 3);
        }

        // Heuristic: try the largest {...} slice that parses
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        while (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
            String candidate = trimmed.substring(firstBrace, lastBrace + 1);
            try {
                return objectMapper.readTree(candidate);
            } catch (Exception ignore) {
                lastBrace = trimmed.lastIndexOf('}', lastBrace - 1);
            }
        }

        throw new IllegalArgumentException("No JSON object found in response");
    }

    // Extract code from ```python ...``` or ```bash ...``` fences if present
    private Optional<Map.Entry<String, String>> extractCodeFromFence(String response) {
        if (response == null) return Optional.empty();
        String s = response;

        int idx = s.indexOf("```");
        while (idx != -1) {
            int end = s.indexOf("```", idx + 3);
            if (end == -1) break;

            int headerEnd = s.indexOf('\n', idx + 3);
            String header = "";
            int contentStart;
            if (headerEnd != -1 && headerEnd < end) {
                header = s.substring(idx + 3, headerEnd).trim().toLowerCase(Locale.ROOT);
                contentStart = headerEnd + 1;
            } else {
                contentStart = idx + 3;
            }
            String code = s.substring(contentStart, end).trim();
            String lang = header;

            if (lang.contains("python")) {
                return Optional.of(Map.entry("python", code));
            } else if (lang.contains("bash") || lang.contains("sh")) {
                return Optional.of(Map.entry("bash", code));
            }

            idx = s.indexOf("```", end + 3);
        }

        return Optional.empty();
    }

    // Abbreviate a potentially long string for logging purposes
    private String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        if (maxLen <= 3) return s.length() <= maxLen ? s : s.substring(0, maxLen);
        return s.length() <= maxLen ? s : (s.substring(0, maxLen - 3) + "...");
    }

    // Normalize read_file("...") or '...' forms and plain filenames
    private String normalizeReadFileCode(String code) {
        if (code == null) return "";
        String s = code.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^\\s*read_file\\s*\\(\\s*['\\\"](.+?)['\\\"]\\s*\\)\\s*;?\\s*$")
                .matcher(s);
        if (m.matches()) {
            return m.group(1);
        }
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    // Parse write_file(...) robustly; prefer "filename\ncontent" format, but also support write_file('filename', <content>)
    private java.util.Map.Entry<String, String> parseWriteFileCode(String code) {
        String s = code == null ? "" : code;
        String t = s.trim();

        // 1) Prefer explicit write_file(...) if present to avoid splitting on newlines inside content
        if (t.toLowerCase(Locale.ROOT).startsWith("write_file")) {
            int open = t.indexOf('(');
            int close = t.lastIndexOf(')');
            if (open != -1 && close > open) {
                String args = t.substring(open + 1, close);
                // Extract first quoted argument as filename, honoring escapes
                int i = 0;
                while (i < args.length() && Character.isWhitespace(args.charAt(i))) i++;
                if (i < args.length() && (args.charAt(i) == '\'' || args.charAt(i) == '"')) {
                    char q = args.charAt(i++);
                    StringBuilder fname = new StringBuilder();
                    boolean escaped = false;
                    while (i < args.length()) {
                        char c = args.charAt(i++);
                        if (escaped) { fname.append(c); escaped = false; continue; }
                        if (c == '\\') { escaped = true; continue; }
                        if (c == q) break;
                        fname.append(c);
                    }
                    // advance to first comma separating args
                    while (i < args.length() && args.charAt(i) != ',') i++;
                    if (i < args.length() && args.charAt(i) == ',') i++;
                    while (i < args.length() && Character.isWhitespace(args.charAt(i))) i++;
                    String contentArg = args.substring(i).trim();
                    // If surrounded by matching quotes, strip outermost quotes
                    if (contentArg.length() >= 2) {
                        char c0 = contentArg.charAt(0);
                        char c1 = contentArg.charAt(contentArg.length() - 1);
                        if ((c0 == '\'' && c1 == '\'') || (c0 == '"' && c1 == '"')) {
                            contentArg = contentArg.substring(1, contentArg.length() - 1);
                        }
                    }
                    // Unescape common sequences that may appear inside the explicit string literal
                    contentArg = contentArg.replace("\\n", "\n")
                                           .replace("\\t", "\t")
                                           .replace("\\r", "\r");
                    return java.util.Map.entry(fname.toString(), contentArg);
                }
            }
        }

        // 2) Fallback format: "filename\ncontent"
        int nl = s.indexOf('\n');
        if (nl >= 0) {
            String filename = s.substring(0, nl).trim();
            String content = s.substring(nl + 1);
            return java.util.Map.entry(filename, content);
        }

        // 3) Fallback: treat the whole string as filename, empty content
        return java.util.Map.entry(t, "");
    }

    private ToolResult executeTool(RlmEnvironment env, ToolCall toolCall) {
        return switch (toolCall.getToolName().toLowerCase()) {
            case "python" -> env.executePython(toolCall.getCode());
            case "bash" -> env.executeBash(toolCall.getCode());
            case "write_file" -> {
                java.util.Map.Entry<String, String> args = parseWriteFileCode(toolCall.getCode());
                yield env.writeFile(args.getKey(), args.getValue());
            }
            case "read_file" -> env.readFile(normalizeReadFileCode(toolCall.getCode()));
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

    private ExecutionResult runCompletion(RlmCompletionRequest request, RlmEnvironment env,
                                          int currentDepth, int maxDepth, int maxBranching) {
        int maxSteps = Math.max(1, maxDepth * 10);
        int step = 0;
        int totalSteps = 0;
        int maxDepthReached = currentDepth;
        int branchCalls = 0;
        boolean finished = false;
        String finalAnswer = null;

        while (!finished && step < maxSteps) {
            step++;
            totalSteps++;
            log.info("RLM Step {}/{} at depth {}", step, maxSteps, currentDepth);

            String userPrompt = promptService.createUserPrompt(
                    request.getQuery(),
                    env.getHistory(),
                    env.getEnvironmentInfo(),
                    currentDepth,
                    maxDepth,
                    maxBranching,
                    branchCalls
            );

            String response = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            StepResponse stepResponse = parseStepResponse(response);

            if (stepResponse.finished) {
                finished = true;
                finalAnswer = stepResponse.answer;
                log.info("RLM finished at step {} depth {}", step, currentDepth);
                break;
            }

            ToolCall toolCall = ToolCall.builder()
                    .toolName(stepResponse.tool)
                    .code(stepResponse.code)
                    .reasoning(stepResponse.thought)
                    .build();

            ToolResult result;
            if ("rlm_call".equalsIgnoreCase(stepResponse.tool)) {
                RecursiveCallResult recursiveCall = executeRecursiveCall(request, env, stepResponse.code,
                        currentDepth, maxDepth, maxBranching, branchCalls);
                result = recursiveCall.toolResult;
                if (result.isSuccess()) {
                    branchCalls++;
                }
                if (recursiveCall.execution != null) {
                    totalSteps += recursiveCall.execution.totalSteps;
                    maxDepthReached = Math.max(maxDepthReached, recursiveCall.execution.maxDepthReached);
                }
            } else {
                result = executeTool(env, toolCall);
                if ("python".equalsIgnoreCase(stepResponse.tool)) {
                    Optional<String> pyReq = consumePythonRlmCallRequest(env);
                    if (pyReq.isPresent()) {
                        RecursiveCallResult recursiveCall = executeRecursiveCall(request, env, pyReq.get(),
                                currentDepth, maxDepth, maxBranching, branchCalls);
                        result = recursiveCall.toolResult;
                        if (result.isSuccess()) {
                            branchCalls++;
                        }
                        if (recursiveCall.execution != null) {
                            totalSteps += recursiveCall.execution.totalSteps;
                            maxDepthReached = Math.max(maxDepthReached, recursiveCall.execution.maxDepthReached);
                        }
                        toolCall = ToolCall.builder()
                                .toolName("rlm_call")
                                .code(pyReq.get())
                                .reasoning(stepResponse.thought)
                                .build();
                    }
                }
            }

            ActionObservation observation = ActionObservation.builder()
                    .step(step)
                    .thought(stepResponse.thought)
                    .action(toolCall)
                    .observation(result)
                    .timestamp(System.currentTimeMillis())
                    .build();

            env.addObservation(observation);

            if (!result.isSuccess()) {
                log.warn("Tool execution failed at step {} depth {}: {}", step, currentDepth, result.getError());
            }
        }

        if (!finished) {
            log.warn("RLM reached max steps ({}) without finishing at depth {}", maxSteps, currentDepth);
            finalAnswer = "Maximum steps reached without complete solution. " +
                         "Last observations: " + summarizeHistory(env.getHistory(), 3);
        }

        return new ExecutionResult(finalAnswer, totalSteps, maxDepthReached);
    }

    private Optional<String> consumePythonRlmCallRequest(RlmEnvironment env) {
        try {
            Path wd = Path.of(env.getCurrentWorkingDirectory());
            Path req = wd.resolve("rlm_tool_request.json");
            if (!Files.exists(req)) return Optional.empty();
            String json = Files.readString(req);
            try { Files.deleteIfExists(req); } catch (Exception ignore) {}
            JsonNode node = objectMapper.readTree(json);
            if (node.has("tool") && "rlm_call".equalsIgnoreCase(node.get("tool").asText())) {
                String code = node.has("code") ? node.get("code").asText() : "";
                return Optional.ofNullable(code);
            }
        } catch (Exception e) {
            log.warn("Failed to consume python rlm_call request: {}", e.toString());
        }
        return Optional.empty();
    }

    private RecursiveCallResult executeRecursiveCall(RlmCompletionRequest request, RlmEnvironment env,
                                                     String subQuery, int currentDepth, int maxDepth,
                                                     int maxBranching, int branchCalls) {
        if (currentDepth + 1 > maxDepth) {
            return new RecursiveCallResult(ToolResult.builder()
                    .success(false)
                    .error("Max recursion depth reached")
                    .build(), null);
        }
        if (branchCalls >= maxBranching) {
            return new RecursiveCallResult(ToolResult.builder()
                    .success(false)
                    .error("Max branching reached at this depth")
                    .build(), null);
        }
        String trimmedQuery = subQuery == null ? "" : subQuery.trim();
        if (trimmedQuery.isEmpty()) {
            return new RecursiveCallResult(ToolResult.builder()
                    .success(false)
                    .error("rlm_call requires a non-empty sub-query")
                    .build(), null);
        }

        RlmEnvironment childEnv = createChildEnvironment(env, currentDepth + 1);
        try {
            RlmCompletionRequest childRequest = RlmCompletionRequest.builder()
                    .query(trimmedQuery)
                    .environmentId(childEnv.getId())
                    .maxDepth(maxDepth)
                    .maxBranching(maxBranching)
                    .verbose(false)
                    .backendHints(request.getBackendHints())
                    .timeout(request.getTimeout())
                    .strategy(request.getStrategy())
                    .build();

            long start = System.currentTimeMillis();
            ExecutionResult childExecution = runCompletion(childRequest, childEnv,
                    currentDepth + 1, maxDepth, maxBranching);
            long duration = System.currentTimeMillis() - start;

            ToolResult toolResult = ToolResult.builder()
                    .success(true)
                    .output(childExecution.finalAnswer)
                    .executionTimeMs(duration)
                    .build();
            return new RecursiveCallResult(toolResult, childExecution);
        } finally {
            environmentStore.deleteEnvironment(childEnv.getId());
        }
    }

    private RlmEnvironment createChildEnvironment(RlmEnvironment parent, int depth) {
        RlmEnvironment child = environmentStore.createEnvironment("child-depth-" + depth);
        String fullContext = parent.getFullContext();
        if (fullContext != null) {
            child.setFullContext(fullContext);
        }
        String initialContext = parent.getContextChunk("initial_context");
        if (initialContext != null) {
            child.putContextChunk("initial_context", initialContext);
        }
        copyWorkingFiles(parent, child);
        return child;
    }

    private void copyWorkingFiles(RlmEnvironment parent, RlmEnvironment child) {
        Path parentDir = Path.of(parent.getCurrentWorkingDirectory());
        Path childDir = Path.of(child.getCurrentWorkingDirectory());
        for (String filename : parent.listFiles()) {
            Path source = parentDir.resolve(filename);
            Path target = childDir.resolve(filename);
            try {
                if (Files.isRegularFile(source)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                log.warn("Failed to copy file {} to child environment: {}", filename, e.getMessage());
            }
        }
    }

    private void seedEnvironmentContext(RlmEnvironment env, String inlineContext) {
        if (inlineContext == null || inlineContext.isBlank()) {
            return;
        }
        env.putContextChunk("initial_context", inlineContext);
        String existingContext = env.getFullContext();
        if (existingContext == null || existingContext.isBlank()) {
            env.setFullContext(inlineContext);
        }
    }

    private static class ExecutionResult {
        final String finalAnswer;
        final int totalSteps;
        final int maxDepthReached;

        ExecutionResult(String finalAnswer, int totalSteps, int maxDepthReached) {
            this.finalAnswer = finalAnswer;
            this.totalSteps = totalSteps;
            this.maxDepthReached = maxDepthReached;
        }
    }

    private static class RecursiveCallResult {
        final ToolResult toolResult;
        final ExecutionResult execution;

        RecursiveCallResult(ToolResult toolResult, ExecutionResult execution) {
            this.toolResult = toolResult;
            this.execution = execution;
        }
    }
}
