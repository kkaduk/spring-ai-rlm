package com.oracle.rlm.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;

@Configuration
public class OpenAIConfig {
    
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    
    @Value("${spring.ai.openai.chat.options.model:gpt-4}")
    private String model;
    
    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;
    
     @Bean
    @Primary
    @ConditionalOnBean(GoogleGenAiChatModel.class)
    public ChatClient.Builder geminiChatClientBuilder(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    @ConditionalOnBean(AnthropicChatModel.class)
    public ChatClient.Builder anthropicChatClientBuilder(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    @ConditionalOnBean(OpenAiChatModel.class)
    public ChatClient.Builder openAiChatClientBuilder(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
    
}
