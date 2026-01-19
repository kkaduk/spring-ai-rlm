package com.oracle.rlm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SpringAiRlmApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SpringAiRlmApplication.class, args);
    }
}