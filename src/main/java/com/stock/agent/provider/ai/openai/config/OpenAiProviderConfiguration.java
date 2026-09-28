package com.stock.agent.provider.ai.openai.config;

import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "agent.provider.ai.openai",
        name = "enabled",
        havingValue = "true"
)
public class OpenAiProviderConfiguration {

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
                properties.apiKey(),
                Clock.systemUTC()
        );
    }
}
