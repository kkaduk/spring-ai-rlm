package com.oracle.rlm.controller;

import com.oracle.rlm.model.RlmRequest;
import com.oracle.rlm.model.RlmResponse;
import com.oracle.rlm.service.RlmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    
    @PostMapping(value = "/solve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RlmResponse> solveProblemMultipart(
            @RequestParam("problem") String problem,
            @RequestParam(value = "maxDepth", required = false) Integer maxDepth,
            @RequestParam(value = "maxBranching", required = false) Integer maxBranching,
            @RequestParam(value = "strategy", required = false) String strategy,
            @RequestParam(value = "verbose", required = false) Boolean verbose,
            @RequestPart(value = "context", required = false) MultipartFile contextFile) {
        log.info("Received RLM solve request (multipart)");
        try {
            if (problem == null || problem.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(RlmResponse.builder()
                                .problem(null)
                                .finalAnswer("Validation error: 'problem' must not be blank.")
                                .build());
            }

            String context = null;
            if (contextFile != null && !contextFile.isEmpty()) {
                context = new String(contextFile.getBytes(), StandardCharsets.UTF_8);
            }

            // Build request manually to avoid data binder attempting to bind file to String field
            com.oracle.rlm.model.RlmRequest req = com.oracle.rlm.model.RlmRequest.builder()
                    .problem(problem)
                    .maxDepth(maxDepth)
                    .maxBranching(maxBranching)
                    .strategy(strategy)
                    .verbose(verbose)
                    .context(context)
                    .build();

            RlmResponse response = rlmService.processRequest(req);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RlmResponse.builder()
                    .problem(problem)
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
