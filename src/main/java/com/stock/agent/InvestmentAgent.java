package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.price.CurrentPriceSnapshot;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class InvestmentAgent {

    public InvestmentDecision decide(HarnessRunContext context) {
        String candidateSymbol = firstCandidateSymbol(context);
        HarnessToolExecutionResult currentPriceResult =
                findCurrentPriceResult(context, candidateSymbol)
                        .orElseThrow(() -> new IllegalStateException(
                                "Current price tool result is required. symbol="
                                        + candidateSymbol
                        ));
        CurrentPriceSnapshot currentPrice =
                currentPriceResult.output().currentPriceSnapshot();

        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Current price received. symbol="
                        + currentPrice.symbol()
                        + ", priceKrw="
                        + currentPrice.priceKrw()
                        + ", source="
                        + currentPriceResult.output().currentPriceSource()
        );
    }

    public AgentNextAction next(HarnessRunContext context) {
        String candidateSymbol = firstCandidateSymbol(context);
        if (findCurrentPriceResult(context, candidateSymbol).isEmpty()) {
            return AgentNextAction.requestTool(
                    HarnessToolRequest.currentPrice(candidateSymbol)
            );
        }

        return AgentNextAction.finalDecision(decide(context));
    }

    private String firstCandidateSymbol(HarnessRunContext context) {
        Objects.requireNonNull(context, "context must not be null.");
        if (context.candidateSymbols().isEmpty()) {
            throw new IllegalStateException(
                    "Investment agent requires at least one candidate symbol."
            );
        }
        return context.candidateSymbols().getFirst();
    }

    private Optional<HarnessToolExecutionResult> findCurrentPriceResult(
            HarnessRunContext context,
            String candidateSymbol
    ) {
        return context.toolResults().stream()
                .filter(result ->
                        result.status() == HarnessToolExecutionStatus.EXECUTED
                )
                .filter(result -> result.type() == HarnessToolType.GET_CURRENT_PRICE)
                .filter(result -> result.request() != null)
                .filter(result -> Objects.equals(
                        result.request().symbol(),
                        candidateSymbol
                ))
                .filter(result -> result.output() != null)
                .filter(result -> result.output().currentPriceSnapshot() != null)
                .filter(result -> Objects.equals(
                        result.output().currentPriceSnapshot().symbol(),
                        candidateSymbol
                ))
                .findFirst();
    }
}
