package com.oracle.rlm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "rlm.security")
@Data
public class RlmSecurityConfig {
    private boolean allowNetwork = false;
    private boolean allowFileSystem = true;
    private int maxFileSizeMb = 10;
    private List<String> allowedCommands = List.of("python3", "bash");
    private int maxExecutionTimeSeconds = 30;
}