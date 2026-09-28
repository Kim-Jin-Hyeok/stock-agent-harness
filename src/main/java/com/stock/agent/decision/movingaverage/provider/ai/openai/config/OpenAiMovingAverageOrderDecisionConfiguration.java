package com.stock.agent.decision.movingaverage.provider.ai.openai.config;

import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.request.OpenAiResponsesRequestFactory;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.response.OpenAiResponsesResponseInterpreter;
import com.stock.agent.decision.movingaverage.provider.ai.openai.execution.OpenAiMovingAverageOrderDecisionExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponseMapper;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.agent.provider.ai.openai.config.OpenAiProviderProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "agent.decision.moving-average",
        name = "provider-type",
        havingValue = "AI"
)
public class OpenAiMovingAverageOrderDecisionConfiguration {

    @Bean
    public RestClient openAiRestClient(
            OpenAiProviderProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public OpenAiResponsesClient openAiResponsesClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiProviderProperties properties
    ) {
        return new OpenAiResponsesClient(
                restClient,
                properties.apiKey()
        );
    }

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
