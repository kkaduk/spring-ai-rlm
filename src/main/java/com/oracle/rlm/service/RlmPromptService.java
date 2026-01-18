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
            - rlm_call: Make a recursive RLM call with a sub-query
            - finish: Return the final answer
            
            For each step, you should:
            1. Think about what action to take next
            2. Choose a tool and provide the code/command
            3. Observe the result
            4. Decide the next action based on the observation
            
            Continue this process until you have solved the problem, then use the 'finish' tool with your answer.

            The full context is stored in the environment, available as:
            - a file named "context.txt" in the working directory
            - a Python variable CONTEXT (auto-loaded for python tool calls)

            When you need to solve a sub-problem, use the rlm_call tool with the
            sub-query in the "code" field. Do not include the full context in the prompt.
            If you are writing Python code, you can alternatively call rlm_call("sub-query") to schedule a recursive call; the orchestrator will execute it after the python step.
            
            IMPORTANT OUTPUT REQUIREMENTS:
            - Respond ONLY with a single valid JSON object. 
            - Do NOT include any prose, explanations, or code fences (no ```).
            - The JSON must include these fields exactly when continuing:
              { "thought": "...", "tool": "tool_name", "code": "code or command", "finished": false }
            - When you are done, respond with:
              { "thought": "summary", "tool": "finish", "answer": "final answer", "finished": true }
            - Valid tool_name values: "python", "bash", "write_file", "read_file", "search", "rlm_call", "finish".
            - Place any code/command to execute in the "code" field.
            
            Tool input formats (STRICT):
            - write_file:
              The "code" MUST be ONE of the following:
              1) "FILENAME\nCONTENT"  (filename on first line, then a newline, then the full file content)
              2) write_file("FILENAME", "CONTENT")
              Notes:
              - Do NOT use code fences.
              - CONTENT may be long and include newlines; include the full content.
              - If writing context.txt, use exactly "context.txt" as the filename.
              Example:
              {
                "thought": "persist architecture draft",
                "tool": "write_file",
                "code": "architecture_vision_draft.txt\nLine 1\nLine 2\nLine 3",
                "finished": false
              }
            - read_file:
              The "code" MUST be either:
              1) "FILENAME"
              2) read_file("FILENAME")

            Example (continue):
            {
              "thought": "your reasoning about what to do next",
              "tool": "tool_name",
              "code": "code or command to execute",
              "finished": false
            }

            Example (finish):
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
                                    String environmentInfo, int currentDepth,
                                    int maxDepth, int maxBranching, int branchCallsSoFar) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("TASK:\n").append(task).append("\n\n");
        prompt.append("RECURSION:\n")
              .append("currentDepth=").append(currentDepth)
              .append(", maxDepth=").append(maxDepth)
              .append(", maxBranching=").append(maxBranching)
              .append(", branchingUsed=").append(branchCallsSoFar)
              .append("\n\n");
        
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
