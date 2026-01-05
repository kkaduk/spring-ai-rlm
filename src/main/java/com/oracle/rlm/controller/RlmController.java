package com.oracle.rlm.controller;

import com.oracle.rlm.model.RlmRequest;
import com.oracle.rlm.model.RlmResponse;
import com.oracle.rlm.service.RlmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rlm")
@RequiredArgsConstructor
@Slf4j
public class RlmController {
    
    private final RlmService rlmService;
    
    @PostMapping("/solve")
    public ResponseEntity<RlmResponse> solveProblem(@Valid @RequestBody RlmRequest request) {
        log.info("Received RLM solve request");
        try {
            RlmResponse response = rlmService.processRequest(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RlmResponse.builder()
                    .problem(request.getProblem())
                    .finalAnswer("Error: " + e.getMessage())
                    .build());
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Spring AI RLM"
        ));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "error", e.getMessage(),
                "type", e.getClass().getSimpleName()
            ));
    }
}