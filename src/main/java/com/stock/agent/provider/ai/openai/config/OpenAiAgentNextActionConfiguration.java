package com.stock.agent.provider.ai.openai.config;

import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.agent.provider.ai.openai.client.request.OpenAiAgentNextActionRequestFactory;
import com.stock.agent.provider.ai.openai.client.response.OpenAiAgentNextActionResponseInterpreter;
import com.stock.agent.provider.ai.openai.execution.OpenAiAgentNextActionExecutor;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "agent.next-action",
        name = "provider-type",
        havingValue = "AI"
)
public class OpenAiAgentNextActionConfiguration {

    @Bean
    public AgentNextActionAiExecutor openAiAgentNextActionExecutor(
            OpenAiAgentNextActionRequestFactory requestFactory,
            OpenAiResponsesClient client,
            OpenAiAgentNextActionResponseInterpreter responseInterpreter,
            OpenAiAgentNextActionResponseMapper responseMapper
    ) {
        return new OpenAiAgentNextActionExecutor(
                requestFactory,
                client,
                responseInterpreter,
                responseMapper
        );
    }
}
