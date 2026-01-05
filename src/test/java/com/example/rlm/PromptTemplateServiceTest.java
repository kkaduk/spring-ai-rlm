package com.example.rlm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.oracle.rlm.service.PromptTemplateService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PromptTemplateServiceTest {
    
    @Autowired
    private PromptTemplateService promptTemplateService;
    
    @Test
    void testDecomposePromptCreation() {
        String prompt = promptTemplateService.createDecomposePrompt(
            "What is the meaning of life?",
            "Philosophy context",
            3
        );
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("What is the meaning of life?"));
        assertTrue(prompt.contains("3"));
        assertTrue(prompt.contains("Philosophy context"));
    }
    
    @Test
    void testSolvePromptCreation() {
        String prompt = promptTemplateService.createSolvePrompt(
            "Calculate 2+2",
            null
        );
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("Calculate 2+2"));
        assertFalse(prompt.contains("{context}"));
    }
    
    @Test
    void testAggregatePromptCreation() {
        List<String> subProblems = Arrays.asList(
            "Sub-problem 1",
            "Sub-problem 2"
        );
        List<String> solutions = Arrays.asList(
            "Solution 1",
            "Solution 2"
        );
        
        String prompt = promptTemplateService.createAggregatePrompt(
            "Original problem",
            subProblems,
            solutions,
            "Test context"
        );
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("Original problem"));
        assertTrue(prompt.contains("Sub-problem 1"));
        assertTrue(prompt.contains("Solution 1"));
    }
}