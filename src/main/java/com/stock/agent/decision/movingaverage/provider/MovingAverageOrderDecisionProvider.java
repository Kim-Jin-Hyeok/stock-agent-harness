package com.stock.agent.decision.movingaverage.provider;

import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;

public interface MovingAverageOrderDecisionProvider {
    OrderQuantityProposal propose(
            MovingAverageOrderDecisionContext context
    );
}
