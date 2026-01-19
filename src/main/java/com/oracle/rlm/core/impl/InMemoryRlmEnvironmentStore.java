package com.oracle.rlm.core.impl;

import com.oracle.rlm.core.RlmEnvironment;
import com.oracle.rlm.core.RlmEnvironmentStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRlmEnvironmentStore implements RlmEnvironmentStore {

    private final Map<String, RlmEnvironment> envs = new ConcurrentHashMap<>();

    @Override
    public RlmEnvironment createEnvironment(String label) {
        String id = UUID.randomUUID().toString();
        RlmEnvironment env = new ExecutableRlmEnvironment(id, label);
        envs.put(id, env);
        return env;
    }

    @Override
    public Optional<RlmEnvironment> getEnvironment(String id) {
        return Optional.ofNullable(envs.get(id));
    }

    @Override
    public void deleteEnvironment(String id) {
        envs.remove(id);
    }
}