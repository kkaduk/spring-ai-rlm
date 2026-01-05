package com.oracle.rlm.core;

public interface RlmClient {

    /**
     * Drop-in replacement for a normal LLM completion call, with recursive reasoning.
     *
     * @param request  the RLM request, including query, environmentId, etc.
     * @return         a structured RLM result with final answer and optional reasoning tree.
     */
    RlmCompletionResult completion(RlmCompletionRequest request);
}
