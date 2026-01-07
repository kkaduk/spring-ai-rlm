package com.oracle.rlm.service;

import com.oracle.rlm.core.ActionObservation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RlmPromptService {
    
    /**
     * Creates the system prompt that defines the RLM paradigm.
     * Based on the RLM paper's approach.
     */
    public String createSystemPrompt() {
        return """
            You are an AI assistant that solves problems by writing and executing code in a persistent environment.
            
            You have access to the following tools:
            - python: Execute Python code
            - bash: Execute bash commands
            - write_file: Write content to a file
            - read_file: Read content from a file
            - search: Search through available context
            - finish: Return the final answer
            
            For each step, you should:
            1. Think about what action to take next
            2. Choose a tool and provide the code/command
            3. Observe the result
            4. Decide the next action based on the observation
            
            Continue this process until you have solved the problem, then use the 'finish' tool with your answer.
            
            Format your response as JSON:
            {
              "thought": "your reasoning about what to do next",
              "tool": "tool_name",
              "code": "code or command to execute",
              "finished": false
            }
            
            When you're done:
            {
              "thought": "summary of solution",
              "tool": "finish",
              "answer": "final answer",
              "finished": true
            }
            """;
    }
    
    /**
     * Creates the user prompt with task and history.
     */
    public String createUserPrompt(String task, List<ActionObservation> history, 
                                    String environmentInfo) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("TASK:\n").append(task).append("\n\n");
        
        prompt.append("ENVIRONMENT:\n").append(environmentInfo).append("\n\n");
        
        if (!history.isEmpty()) {
            prompt.append("PREVIOUS ACTIONS:\n");
            for (ActionObservation obs : history) {
                prompt.append(String.format("""
                    Step %d:
                    Thought: %s
                    Action: %s
                    Code: %s
                    Observation: %s
                    
                    """,
                    obs.getStep(),
                    obs.getThought(),
                    obs.getAction().getToolName(),
                    obs.getAction().getCode(),
                    obs.getObservation().isSuccess() 
                        ? obs.getObservation().getOutput()
                        : "ERROR: " + obs.getObservation().getError()
                ));
            }
        }
        
        prompt.append("What is your next action?");
        
        return prompt.toString();
    }
}