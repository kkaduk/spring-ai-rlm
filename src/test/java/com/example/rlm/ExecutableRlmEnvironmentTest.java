package com.example.rlm;

import com.oracle.rlm.core.ToolResult;
import com.oracle.rlm.core.impl.ExecutableRlmEnvironment;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutableRlmEnvironmentTest {

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    void testSetFullContextWritesNonEmptyFile() throws Exception {
        String id = UUID.randomUUID().toString();
        ExecutableRlmEnvironment env = new ExecutableRlmEnvironment(id, "test-context");
        Path workDir = Path.of(env.getCurrentWorkingDirectory());
        Path contextPath = workDir.resolve("context.txt");
        try {
            // Initially created file exists and may be empty
            assertTrue(Files.exists(contextPath), "context.txt should exist after environment init");

            // Set non-empty context and verify it is persisted to disk
            String ctx = "Hello Context " + System.nanoTime();
            env.setFullContext(ctx);

            String disk = Files.readString(contextPath);
            assertEquals(ctx, disk, "context.txt content on disk should match setFullContext payload");
            assertEquals(ctx, env.getFullContext(), "getFullContext should return the stored content");
            assertEquals(ctx.length(), env.getContextSize(), "context size should reflect stored content length");
        } finally {
            deleteRecursively(workDir);
        }
    }

    @Test
    void testWriteFileCreatesAndWritesContent() throws Exception {
        String id = UUID.randomUUID().toString();
        ExecutableRlmEnvironment env = new ExecutableRlmEnvironment(id, "test-write");
        Path workDir = Path.of(env.getCurrentWorkingDirectory());
        try {
            // Write a regular file
            String filename = "notes/sub/outline.txt";
            String content = "Line1\nLine2\nLine3";
            ToolResult tr = env.writeFile(filename, content);
            assertTrue(tr.isSuccess(), "writeFile should succeed");

            Path written = workDir.resolve(filename);
            assertTrue(Files.exists(written), "Written file should exist");
            String disk = Files.readString(written);
            assertEquals(content, disk, "Written file content should match provided content");

            // Writing context.txt updates context state
            String ctx = "Context From WriteFile " + System.nanoTime();
            ToolResult tr2 = env.writeFile("context.txt", ctx);
            assertTrue(tr2.isSuccess(), "writeFile for context.txt should succeed");
            assertEquals(ctx, env.getFullContext(), "getFullContext should reflect content written via writeFile");
            assertEquals(ctx.length(), env.getContextSize(), "context size should be updated");
        } finally {
            deleteRecursively(workDir);
        }
    }
}
