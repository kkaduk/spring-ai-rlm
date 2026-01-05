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
        RlmEnvironment env = new SimpleRlmEnvironment(id, label);
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

    private static class SimpleRlmEnvironment implements RlmEnvironment {

        private final String id;
        private final String label;
        private final Map<String, String> chunks = new ConcurrentHashMap<>();

        private SimpleRlmEnvironment(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getLabel() {
            return label;
        }

        @Override
        public String getContextChunk(String key) {
            return chunks.get(key);
        }

        @Override
        public void putContextChunk(String key, String value) {
            chunks.put(key, value);
        }

        @Override
        public String search(String query) {
            // naive: search across values and return some concatenation
            return chunks.entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue().contains(query))
                    .map(Map.Entry::getValue)
                    .limit(5)
                    .reduce("", (a, b) -> a + "\n\n" + b);
        }
    }
}
