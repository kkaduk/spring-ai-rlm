package com.oracle.rlm.core.impl;

import com.oracle.rlm.core.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class ExecutableRlmEnvironment implements RlmEnvironment {

    private static final String CONTEXT_FILENAME = "context.txt";
    
    private final String id;
    private final String label;
    private final Path workDir;
    private final Map<String, String> chunks = new ConcurrentHashMap<>();
    private final List<ActionObservation> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Path contextPath;
    private long contextSize;
    
    public ExecutableRlmEnvironment(String id, String label) {
        this.id = id;
        this.label = label;
        try {
            // this.workDir = Files.createTempDirectory("rlm_env_" + id);
            this.workDir = Files.createDirectory(Paths.get("rlm_env_" + id));
            log.info("Created work directory: {}", workDir);
            // Ensure context file exists so read_file('context.txt') doesn't fail
            this.contextPath = workDir.resolve(CONTEXT_FILENAME);
            if (!Files.exists(this.contextPath)) {
                Files.writeString(this.contextPath, "");
            }
            this.contextSize = Files.size(this.contextPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create work directory", e);
        }
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getLabel() {
        return label;
    }
    
    @Override
    public String getContextChunk(String key) {
        return chunks.get(key);
    }
    
    @Override
    public void putContextChunk(String key, String value) {
        chunks.put(key, value);
    }

    @Override
    public void setFullContext(String context) {
        try {
            if (context == null) {
                contextSize = 0;
                if (contextPath != null) {
                    Files.deleteIfExists(contextPath);
                }
                contextPath = null;
                return;
            }
            contextPath = workDir.resolve(CONTEXT_FILENAME);
            Files.writeString(contextPath, context);
            contextSize = context.length();
        } catch (IOException e) {
            log.error("Failed to store full context", e);
            throw new RuntimeException("Failed to store full context", e);
        }
    }

    @Override
    public String getFullContext() {
        if (contextPath == null || !Files.exists(contextPath)) {
            return null;
        }
        try {
            return Files.readString(contextPath);
        } catch (IOException e) {
            log.error("Failed to read full context", e);
            return null;
        }
    }

    @Override
    public long getContextSize() {
        return contextSize;
    }
    
    @Override
    public ToolResult executePython(String code) {
        long start = System.currentTimeMillis();
        try {
            // Write code to temp file
            Path scriptPath = workDir.resolve("script_" + System.nanoTime() + ".py");
            String wrappedCode = buildPythonPrelude() + "\n" + code;
            Files.writeString(scriptPath, wrappedCode);
            
            // Execute with timeout
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath.toAbsolutePath().toString());
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);
            
            Process process = pb.start();
            
            // Capture output with timeout
            Future<String> outputFuture = executor.submit(() -> 
                new String(process.getInputStream().readAllBytes()));
            Future<String> errorFuture = executor.submit(() -> 
                new String(process.getErrorStream().readAllBytes()));
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.builder()
                    .success(false)
                    .error("Execution timeout (30s)")
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
            }
            
            String output = outputFuture.get(1, TimeUnit.SECONDS);
            String error = errorFuture.get(1, TimeUnit.SECONDS);
            
            return ToolResult.builder()
                .success(process.exitValue() == 0)
                .output(output)
                .error(error)
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
                
        } catch (Exception e) {
            log.error("Python execution failed", e);
            return ToolResult.builder()
                .success(false)
                .error(e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
        }
    }
    
    @Override
    public ToolResult executeBash(String command) {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(workDir.toFile());
            
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.builder()
                    .success(false)
                    .error("Execution timeout")
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
            }
            
            String output = new String(process.getInputStream().readAllBytes());
            String error = new String(process.getErrorStream().readAllBytes());
            
            return ToolResult.builder()
                .success(process.exitValue() == 0)
                .output(output)
                .error(error)
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
                
        } catch (Exception e) {
            return ToolResult.builder()
                .success(false)
                .error(e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
        }
    }
    
    @Override
    public ToolResult writeFile(String filename, String content) {
        try {
            if (filename == null || filename.isBlank()) {
                return ToolResult.builder()
                    .success(false)
                    .error("write_file requires a non-empty filename")
                    .build();
            }
            Path filePath = workDir.resolve(filename);
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            String safeContent = content == null ? "" : content;
            Files.writeString(filePath, safeContent);
            if (CONTEXT_FILENAME.equals(filePath.getFileName().toString())) {
                contextPath = filePath;
                contextSize = safeContent.length();
            }
            return ToolResult.builder()
                .success(true)
                .output("File written: " + filePath.getFileName())
                .build();
        } catch (IOException e) {
            return ToolResult.builder()
                .success(false)
                .error(e.getMessage())
                .build();
        }
    }
    
    @Override
    public ToolResult readFile(String filename) {
        try {
            Path filePath = workDir.resolve(filename);
            String content = Files.readString(filePath);
            return ToolResult.builder()
                .success(true)
                .output(content)
                .build();
        } catch (IOException e) {
            return ToolResult.builder()
                .success(false)
                .error(e.getMessage())
                .build();
        }
    }
    
    @Override
    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        StringBuilder results = new StringBuilder();
        String normalized = query.toLowerCase();

        String contextSnippet = findInContextSnippet(normalized);
        if (contextSnippet != null) {
            results.append("full_context: ").append(contextSnippet);
        }

        chunks.entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue().toLowerCase()
                .contains(normalized))
            .map(e -> e.getKey() + ": " + e.getValue())
            .limit(5)
            .forEach(match -> {
                if (!results.isEmpty()) {
                    results.append("\n\n");
                }
                results.append(match);
            });

        return results.toString();
    }
    
    @Override
    public List<ActionObservation> getHistory() {
        return new ArrayList<>(history);
    }
    
    @Override
    public void addObservation(ActionObservation observation) {
        history.add(observation);
    }
    
    @Override
    public String getCurrentWorkingDirectory() {
        return workDir.toString();
    }
    
    @Override
    public List<String> listFiles() {
        try {
            return Files.list(workDir)
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getEnvironmentInfo() {
        return String.format("""
            Environment ID: %s
            Working Directory: %s
            Files: %s
            Context Size: %d
            Context Chunks: %d
            History Steps: %d
            """, 
            id, workDir, listFiles(), contextSize, chunks.size(), history.size());
    }

    private String buildPythonPrelude() {
        return """
            from pathlib import Path
            import json
            CONTEXT_PATH = Path("%s")
            CONTEXT = CONTEXT_PATH.read_text() if CONTEXT_PATH.exists() else ""
            WORKDIR = str(Path(".").resolve())

            # Python-to-RLM tool bridge: request an RLM tool by writing a JSON file the orchestrator will consume.
            def rlm_call(sub_query):
                try:
                    req = {"tool": "rlm_call", "code": str(sub_query)}
                    Path("rlm_tool_request.json").write_text(json.dumps(req))
                    print("RLM_TOOL_REQUEST: rlm_call scheduled")
                except Exception as e:
                    print(f"ERROR: failed to schedule rlm_call: {e}")
            """.formatted(CONTEXT_FILENAME);
    }

    private String findInContextSnippet(String normalizedQuery) {
        if (contextPath == null || normalizedQuery == null || normalizedQuery.isBlank()) {
            return null;
        }
        try {
            String context = Files.readString(contextPath);
            String lowerContext = context.toLowerCase();
            int index = lowerContext.indexOf(normalizedQuery);
            if (index < 0) {
                return null;
            }
            int start = Math.max(0, index - 200);
            int end = Math.min(context.length(), index + 200);
            return context.substring(start, end);
        } catch (IOException e) {
            log.error("Failed to search full context", e);
            return null;
        }
    }
}
