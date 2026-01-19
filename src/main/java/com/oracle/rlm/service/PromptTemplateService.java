package com.oracle.rlm.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptTemplateService {
    
    public String createDecomposePrompt(String problem, String context, int maxBranching) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert problem solver using recursive decomposition.\n\n");
        
        if (context != null && !context.isBlank()) {
            prompt.append("Context: ").append(context).append("\n\n");
        }
        
        prompt.append("Problem: ").append(problem).append("\n\n");
        prompt.append("Analyze this problem and determine if it can be solved directly or needs to be decomposed.\n\n");
        prompt.append("If it needs decomposition:\n");
        prompt.append("1. Break it down into ").append(maxBranching).append(" or fewer simpler sub-problems\n");
        prompt.append("2. Each sub-problem should be independent and simpler than the original\n");
        prompt.append("3. Ensure sub-problems, when solved, can be combined to solve the original problem\n\n");
        prompt.append("If it can be solved directly:\n");
        prompt.append("1. Indicate that no decomposition is needed\n");
        prompt.append("2. Provide reasoning for why it's a base case\n\n");
        prompt.append("Respond in the following JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"needsDecomposition\": true/false,\n");
        prompt.append("  \"reasoning\": \"explanation\",\n");
        prompt.append("  \"subProblems\": [\"sub-problem 1\", \"sub-problem 2\", ...]\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    public String createSolvePrompt(String problem, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert problem solver.\n\n");
        
        if (context != null && !context.isBlank()) {
            prompt.append("Context: ").append(context).append("\n\n");
        }
        
        prompt.append("Problem: ").append(problem).append("\n\n");
        prompt.append("Solve this problem directly and provide a clear, concise answer.\n");
        prompt.append("Include your reasoning and any important considerations.\n\n");
        prompt.append("Respond in the following JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"solution\": \"your solution\",\n");
        prompt.append("  \"reasoning\": \"your reasoning\",\n");
        prompt.append("  \"confidence\": 0.0-1.0\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    public String createAggregatePrompt(String originalProblem, List<String> subProblems, 
                                       List<String> solutions, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert at synthesizing solutions.\n\n");
        
        if (context != null && !context.isBlank()) {
            prompt.append("Context: ").append(context).append("\n\n");
        }
        
        prompt.append("Original Problem: ").append(originalProblem).append("\n\n");
        prompt.append("Sub-problems and their solutions:\n");
        
        for (int i = 0; i < subProblems.size(); i++) {
            prompt.append("\nSub-problem ").append(i + 1).append(": ").append(subProblems.get(i)).append("\n");
            prompt.append("Solution ").append(i + 1).append(": ").append(solutions.get(i)).append("\n");
        }
        
        prompt.append("\nCombine these solutions to answer the original problem.\n");
        prompt.append("Ensure your answer is coherent, comprehensive, and directly addresses the original problem.\n\n");
        prompt.append("Respond in the following JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"finalAnswer\": \"synthesized answer\",\n");
        prompt.append("  \"reasoning\": \"how you combined the solutions\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
}