package com.stock.agent.provider;

import com.stock.agent.AgentNextAction;
import com.stock.harness.HarnessRunContext;

public interface AgentNextActionProvider {
    AgentNextAction next(HarnessRunContext context);
}
