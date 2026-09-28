package com.stock.agent.decision.movingaverage.provider;

import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.provider.result.MovingAverageOrderDecisionProviderResult;

public interface MovingAverageOrderDecisionProvider {
    MovingAverageOrderDecisionProviderResult propose(
            MovingAverageOrderDecisionContext context
    );
}
