package com.stock.harness.execution;

import com.stock.agent.InvestmentDecision;
import com.stock.harness.tool.HarnessToolExecutionResult;

import java.util.List;

public record HarnessAgentLoopResult(
        InvestmentDecision decision,
        List<HarnessToolExecutionResult> toolResults
) {
    public HarnessAgentLoopResult {
        toolResults = List.copyOf(toolResults);
    }
}
