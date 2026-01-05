package com.oracle.rlm.service;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PromptTemplateServiceWithSpringAI {
    
    @Value("classpath:prompts/decompose-prompt.st")
    private Resource decomposeTemplate;
    
    @Value("classpath:prompts/solve-prompt.st")
    private Resource solveTemplate;
    
    @Value("classpath:prompts/aggregate-prompt.st")
    private Resource aggregateTemplate;
    
    public String createDecomposePrompt(String problem, String context, int maxBranching) {
        Map<String, Object> model = new HashMap<>();
        model.put("problem", problem);
        model.put("maxBranching", maxBranching);
        model.put("context", context != null ? context : "");
        model.put("hasContext", context != null && !context.isBlank());
        
        PromptTemplate promptTemplate = new PromptTemplate(decomposeTemplate);
        return promptTemplate.render(model);
    }
    
    public String createSolvePrompt(String problem, String context) {
        Map<String, Object> model = new HashMap<>();
        model.put("problem", problem);
        model.put("context", context != null ? context : "");
        model.put("hasContext", context != null && !context.isBlank());
        
        PromptTemplate promptTemplate = new PromptTemplate(solveTemplate);
        return promptTemplate.render(model);
    }
    
    public String createAggregatePrompt(String originalProblem, List<String> subProblems, 
                                       List<String> solutions, String context) {
        Map<String, Object> model = new HashMap<>();
        model.put("originalProblem", originalProblem);
        model.put("subProblems", subProblems);
        model.put("solutions", solutions);
        model.put("context", context != null ? context : "");
        model.put("hasContext", context != null && !context.isBlank());
        
        // Build indexed list for template
        List<Map<String, Object>> subProblemsList = new java.util.ArrayList<>();
        for (int i = 0; i < subProblems.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("index", i + 1);
            item.put("subProblem", subProblems.get(i));
            item.put("solution", solutions.get(i));
            subProblemsList.add(item);
        }
        model.put("subProblemsAndSolutions", subProblemsList);
        
        PromptTemplate promptTemplate = new PromptTemplate(aggregateTemplate);
        return promptTemplate.render(model);
    }
}