package com.oracle.rlm.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class OpenAIConfig {

    /**
     * Provide a single ChatClient.Builder bean by selecting the first available ChatModel.
     * Prefer OpenAI, then Anthropic, then Google. If none are available, fail fast
     * with a clear message (e.g., missing API key configuration).
     */
    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(
            ObjectProvider<OpenAiChatModel> openAiProvider,
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<GoogleGenAiChatModel> googleProvider) {

        ChatModel model = openAiProvider.getIfAvailable();
        if (model == null) {
            model = anthropicProvider.getIfAvailable();
        }
        if (model == null) {
            model = googleProvider.getIfAvailable();
        }

        if (model == null) {
            throw new IllegalStateException(
                "No ChatModel bean available. Ensure at least one provider is configured "
                + "(e.g., set spring.ai.openai.api-key/OPENAI_API_KEY or the relevant provider settings)."
            );
        }

        return ChatClient.builder(model);
    }
}
