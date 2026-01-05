package com.example.rlm;

import com.oracle.rlm.model.RlmRequest;
import com.oracle.rlm.model.RlmResponse;
import com.oracle.rlm.service.RlmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=test-key"
})
class RlmServiceTest {
    
    @Autowired(required = false)
    private RlmService rlmService;
    
    @Test
    void testContextLoads() {
        // Test that Spring context loads successfully
        assertNotNull(rlmService);
    }
    
    @Test
    void testRlmRequestBuilder() {
        RlmRequest request = RlmRequest.builder()
            .problem("What is 2+2?")
            .maxDepth(2)
            .maxBranching(2)
            .strategy("depth-first")
            .verbose(true)
            .build();
        
        assertNotNull(request);
        assertEquals("What is 2+2?", request.getProblem());
        assertEquals(2, request.getMaxDepth());
        assertEquals(2, request.getMaxBranching());
    }
}