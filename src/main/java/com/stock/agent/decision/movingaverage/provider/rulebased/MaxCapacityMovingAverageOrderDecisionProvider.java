package com.stock.agent.decision.movingaverage.provider.rulebased;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.provider.MovingAverageOrderDecisionProvider;
import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.movingaverage.provider.result.MovingAverageOrderDecisionProviderResult;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.risk.capacity.OrderQuantityCapacity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MaxCapacityMovingAverageOrderDecisionProvider
        implements MovingAverageOrderDecisionProvider {
    private static final MovingAverageOrderDecisionProviderIdentity IDENTITY =
            new MovingAverageOrderDecisionProviderIdentity(
                    "MAX_CAPACITY_RULE_BASED",
                    1
            );

    @Override
    public MovingAverageOrderDecisionProviderResult propose(
            MovingAverageOrderDecisionContext context
    ) {
        Objects.requireNonNull(context, "context must not be null.");

        if (context.signalAction() == InvestmentAction.HOLD) {
            return result(OrderQuantityProposal.hold(
                    "Order skipped because signal action is HOLD. symbol="
                            + context.symbol()
            ));
        }

        OrderQuantityCapacity capacity = context.quantityCapacity();
        if (!capacity.canOrder()) {
            return result(OrderQuantityProposal.hold(
                    "Order skipped because capacity is unavailable. action="
                            + context.signalAction()
                            + ", symbol="
                            + context.symbol()
                            + ", maxAllowedQuantity="
                            + capacity.maxAllowedQuantity()
            ));
        }

        return result(OrderQuantityProposal.execute(
                capacity.maxAllowedQuantity(),
                "Use maximum allowed quantity. action="
                        + context.signalAction()
                        + ", symbol="
                        + context.symbol()
                        + ", quantity="
                        + capacity.maxAllowedQuantity()
        ));
    }

    private MovingAverageOrderDecisionProviderResult result(
            OrderQuantityProposal proposal
    ) {
        return new MovingAverageOrderDecisionProviderResult(
                IDENTITY,
                proposal
        );
    }
}
