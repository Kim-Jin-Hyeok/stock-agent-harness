package com.stock.agent.decision.movingaverage.provider.ai.openai.config;

import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.request.OpenAiResponsesRequestFactory;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.response.OpenAiResponsesResponseInterpreter;
import com.stock.agent.decision.movingaverage.provider.ai.openai.execution.OpenAiMovingAverageOrderDecisionExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponseMapper;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "agent.decision.moving-average",
        name = "provider-type",
        havingValue = "AI"
)
public class OpenAiMovingAverageOrderDecisionConfiguration {

    @Bean
    public MovingAverageOrderDecisionAiExecutor
            openAiMovingAverageOrderDecisionExecutor(
                    OpenAiResponsesRequestFactory requestFactory,
                    OpenAiResponsesClient client,
                    OpenAiResponsesResponseInterpreter responseInterpreter,
                    OpenAiMovingAverageOrderDecisionResponseMapper
                            responseMapper
            ) {
        return new OpenAiMovingAverageOrderDecisionExecutor(
                requestFactory,
                client,
                responseInterpreter,
                responseMapper
        );
    }
}
