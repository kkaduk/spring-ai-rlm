package com.oracle.rlm.strategy;

import com.oracle.rlm.model.RecursionStep;

public interface RecursionStrategy {
    RecursionStep execute(String problem, String context, int maxDepth, int maxBranching);
}