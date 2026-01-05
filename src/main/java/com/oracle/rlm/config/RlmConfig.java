package com.oracle.rlm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rlm")
@Data
public class RlmConfig {
    
    /**
     * Maximum recursion depth
     */
    private int maxDepth = 3;
    
    /**
     * Maximum number of sub-problems at each level
     */
    private int maxBranching = 3;
    
    /**
     * Timeout for each recursive call in seconds
     */
    private int timeoutSeconds = 30;
    
    /**
     * Enable caching of intermediate results
     */
    private boolean enableCaching = true;
    
    /**
     * Temperature for decomposition steps
     */
    private double decompositionTemperature = 0.8;
    
    /**
     * Temperature for solving steps
     */
    private double solvingTemperature = 0.7;
    
    /**
     * Temperature for aggregation steps
     */
    private double aggregationTemperature = 0.6;
}