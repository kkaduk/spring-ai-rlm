package com.oracle.rlm.core;

import java.util.Optional;

public interface RlmEnvironmentStore {

    /**
     * Create a new environment, optionally pre-populated with some data.
     */
    RlmEnvironment createEnvironment(String label);

    /**
     * Lookup by id.
     */
    Optional<RlmEnvironment> getEnvironment(String id);

    /**
     * Remove when no longer needed (optional).
     */
    void deleteEnvironment(String id);
}
