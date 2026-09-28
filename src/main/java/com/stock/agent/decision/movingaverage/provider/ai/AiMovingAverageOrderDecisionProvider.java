package com.stock.agent.decision.movingaverage.provider.ai;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPrompt;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPromptFactory;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequest;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequestFactory;
import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.movingaverage.provider.result.MovingAverageOrderDecisionProviderResult;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.risk.capacity.OrderQuantityCapacity;

import java.util.Objects;

public class AiMovingAverageOrderDecisionProvider
        implements MovingAverageOrderDecisionProvider {
    private static final MovingAverageOrderDecisionProviderIdentity
            INVOCATION_SKIPPED_IDENTITY =
            new MovingAverageOrderDecisionProviderIdentity(
                    "AI_INVOCATION_SKIPPED",
                    1
            );

    private final MovingAverageOrderDecisionAiRequestFactory requestFactory;
    private final MovingAverageOrderDecisionAiPromptFactory promptFactory;
    private final MovingAverageOrderDecisionAiExecutor executor;

    public AiMovingAverageOrderDecisionProvider(
            MovingAverageOrderDecisionAiRequestFactory requestFactory,
            MovingAverageOrderDecisionAiPromptFactory promptFactory,
            MovingAverageOrderDecisionAiExecutor executor
    ) {
        this.requestFactory = Objects.requireNonNull(
                requestFactory,
                "requestFactory must not be null."
        );
        this.promptFactory = Objects.requireNonNull(
                promptFactory,
                "promptFactory must not be null."
        );
        this.executor = Objects.requireNonNull(
                executor,
                "executor must not be null."
        );
    }

    @Override
    public MovingAverageOrderDecisionProviderResult propose(
            MovingAverageOrderDecisionContext context
    ) {
        Objects.requireNonNull(context, "context must not be null.");

        if (context.signalAction() == InvestmentAction.HOLD) {
            return skipped(OrderQuantityProposal.hold(
                    "AI invocation skipped because signal action is HOLD. "
                            + "symbol="
                            + context.symbol()
            ));
        }

        OrderQuantityCapacity capacity = context.quantityCapacity();
        if (!capacity.canOrder()) {
            return skipped(OrderQuantityProposal.hold(
                    "AI invocation skipped because order capacity is "
                            + "unavailable. action="
                            + context.signalAction()
                            + ", symbol="
                            + context.symbol()
                            + ", maxAllowedQuantity="
                            + capacity.maxAllowedQuantity()
            ));
        }

        MovingAverageOrderDecisionAiRequest request =
                requestFactory.create(context);
        MovingAverageOrderDecisionAiPrompt prompt =
                promptFactory.create(request);
        return Objects.requireNonNull(
                executor.execute(prompt),
                "AI executor result must not be null."
        );
    }

    private MovingAverageOrderDecisionProviderResult skipped(
            OrderQuantityProposal proposal
    ) {
        return new MovingAverageOrderDecisionProviderResult(
                INVOCATION_SKIPPED_IDENTITY,
                proposal
        );
    }
}
