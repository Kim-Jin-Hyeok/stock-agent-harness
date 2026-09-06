package com.stock.agent;

import com.stock.harness.tool.HarnessToolRequest;

public record AgentNextAction(
        AgentNextActionType type,
        HarnessToolRequest toolRequest,
        InvestmentDecision investmentDecision
) {
    public static AgentNextAction requestTool(HarnessToolRequest toolRequest) {
        return new AgentNextAction(
                AgentNextActionType.REQUEST_TOOL,
                toolRequest,
                null
        );
    }

    public static AgentNextAction finalDecision(InvestmentDecision investmentDecision) {
        return new AgentNextAction(
                AgentNextActionType.FINAL_DECISION,
                null,
                investmentDecision
        );
    }
}
