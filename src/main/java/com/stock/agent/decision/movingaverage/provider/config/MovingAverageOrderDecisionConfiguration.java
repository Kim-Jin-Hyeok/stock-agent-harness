package com.stock.agent.decision.movingaverage.provider.config;

import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.ai.AiMovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPromptFactory;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequestFactory;
import com.stock.agent.decision.movingaverage.provider.rulebased.MaxCapacityMovingAverageOrderDecisionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MovingAverageOrderDecisionConfiguration {

    @Bean
    public MovingAverageOrderDecisionProvider movingAverageOrderDecisionProvider(
            MovingAverageOrderDecisionProperties properties,
            MovingAverageOrderDecisionAiRequestFactory requestFactory,
            MovingAverageOrderDecisionAiPromptFactory promptFactory,
            ObjectProvider<MovingAverageOrderDecisionAiExecutor>
                    executorProvider
    ) {
        return switch (properties.providerType()) {
            case RULE_BASED ->
                    new MaxCapacityMovingAverageOrderDecisionProvider();
            case AI -> new AiMovingAverageOrderDecisionProvider(
                    requestFactory,
                    promptFactory,
                    requireExecutor(executorProvider)
            );
        };
    }

    private MovingAverageOrderDecisionAiExecutor requireExecutor(
            ObjectProvider<MovingAverageOrderDecisionAiExecutor>
                    executorProvider
    ) {
        MovingAverageOrderDecisionAiExecutor executor =
                executorProvider.getIfAvailable();
        if (executor == null) {
            throw new IllegalStateException(
                    "MovingAverageOrderDecisionAiExecutor must be configured "
                            + "when provider type is AI."
            );
        }
        return executor;
    }
}
