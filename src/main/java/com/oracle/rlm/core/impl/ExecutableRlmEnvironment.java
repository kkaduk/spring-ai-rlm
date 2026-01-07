package com.oracle.rlm.core.impl;

import com.oracle.rlm.core.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class ExecutableRlmEnvironment implements RlmEnvironment {
    
    private final String id;
    private final String label;
    private final Path workDir;
    private final Map<String, String> chunks = new ConcurrentHashMap<>();
    private final List<ActionObservation> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public ExecutableRlmEnvironment(String id, String label) {
        this.id = id;
        this.label = label;
        try {
            this.workDir = Files.createTempDirectory("rlm_env_" + id);
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
    public ToolResult executePython(String code) {
        long start = System.currentTimeMillis();
        try {
            // Write code to temp file
            Path scriptPath = workDir.resolve("script_" + System.nanoTime() + ".py");
            Files.writeString(scriptPath, code);
            
            // Execute with timeout
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath.toString());
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
            Path filePath = workDir.resolve(filename);
            Files.writeString(filePath, content);
            return ToolResult.builder()
                .success(true)
                .output("File written: " + filename)
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
        // Search through stored chunks
        return chunks.entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue().toLowerCase()
                .contains(query.toLowerCase()))
            .map(e -> e.getKey() + ": " + e.getValue())
            .limit(5)
            .reduce("", (a, b) -> a + "\n\n" + b);
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
            Context Chunks: %d
            History Steps: %d
            """, 
            id, workDir, listFiles(), chunks.size(), history.size());
    }
}