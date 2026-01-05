package com.oracle.rlm.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Update version using promp template files from resources
 */

@Service
public class PromptTemplateService2 {
    
    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache = new HashMap<>();
    
    public PromptTemplateService2(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    public String createDecomposePrompt(String problem, String context, int maxBranching) {
        String template = loadTemplate("classpath:prompts/decompose-prompt.st");
        
        return template
            .replace("{problem}", problem)
            .replace("{maxBranching}", String.valueOf(maxBranching))
            .replace("{context}", context != null ? "" : "{context}")
            .replace("{contextValue}", context != null ? context : "")
            .replace("{/context}", context != null ? "" : "{/context}");
    }
    
    public String createSolvePrompt(String problem, String context) {
        String template = loadTemplate("classpath:prompts/solve-prompt.st");
        
        String prompt = template.replace("{problem}", problem);
        
        if (context != null && !context.isBlank()) {
            prompt = prompt
                .replace("{context}", "")
                .replace("{contextValue}", context)
                .replace("{/context}", "");
        } else {
            // Remove context section if not provided
            prompt = prompt
                .replaceAll("\\{context\\}[\\s\\S]*?\\{/context\\}", "");
        }
        
        return prompt;
    }
    
    public String createAggregatePrompt(String originalProblem, List<String> subProblems, 
                                       List<String> solutions, String context) {
        String template = loadTemplate("classpath:prompts/aggregate-prompt.st");
        
        // Build sub-problems and solutions section
        StringBuilder subProblemsSection = new StringBuilder();
        for (int i = 0; i < subProblems.size(); i++) {
            subProblemsSection.append(String.format("Sub-problem %d: %s%n", i + 1, subProblems.get(i)));
            subProblemsSection.append(String.format("Solution %d: %s%n", i + 1, solutions.get(i)));
            subProblemsSection.append("\n---\n\n");
        }
        
        String prompt = template
            .replace("{originalProblem}", originalProblem)
            .replaceAll("\\{subProblemsAndSolutions\\}[\\s\\S]*?\\{/subProblemsAndSolutions\\}", 
                       subProblemsSection.toString());
        
        if (context != null && !context.isBlank()) {
            prompt = prompt
                .replace("{context}", "")
                .replace("{contextValue}", context)
                .replace("{/context}", "");
        } else {
            prompt = prompt
                .replaceAll("\\{context\\}[\\s\\S]*?\\{/context\\}", "");
        }
        
        return prompt;
    }
    
    private String loadTemplate(String path) {
        // Check cache first
        if (templateCache.containsKey(path)) {
            return templateCache.get(path);
        }
        
        try {
            Resource resource = resourceLoader.getResource(path);
            String template = StreamUtils.copyToString(
                resource.getInputStream(), 
                StandardCharsets.UTF_8
            );
            templateCache.put(path, template);
            return template;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }
    
    /**
     * Clear the template cache (useful for development/testing)
     */
    public void clearCache() {
        templateCache.clear();
    }
}