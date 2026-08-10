package com.stock.harness;

import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.market.MarketService;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentHarness {
    private final RiskGuard riskGuard;
    private final TradeExecutor tradeExecutor;
    private final PortfolioService portfolioService;
    private final MarketService marketService;
    private final InvestmentAgent investmentAgent;

    public HarnessRunResult run() {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Investment Harness started.");

        String runId = UUID.randomUUID().toString();

        PortfolioSnapshot portfolioSnapshot = portfolioService.getCurrentSnapshot();
        MarketSnapshot marketSnapshot = marketService.getCurrentSnapshot();

        HarnessRunContext context = new HarnessRunContext(
                runId,
                portfolioSnapshot,
                marketSnapshot
        );

        InvestmentDecision decision = investmentAgent.decide(
                context.portfolioSnapshot(),
                context.marketSnapshot()
        );

        RiskCheckResult riskCheckResult = riskGuard.validate(decision);

        TradeResult tradeResult = tradeExecutor.execute(decision, riskCheckResult);

        List<HarnessStepResult> steps = List.of(
                new HarnessStepResult(HarnessStepType.LOAD_PORTFOLIO, HarnessStepStatus.COMPLETED, "Portfolio loading complete."),
                new HarnessStepResult(HarnessStepType.LOAD_MARKET, HarnessStepStatus.COMPLETED, "Market loading complete."),
                new HarnessStepResult(HarnessStepType.RUN_INVESTMENT_AGENT, HarnessStepStatus.COMPLETED, decision.reason()),
                new HarnessStepResult(HarnessStepType.VALIDATE_DECISION, riskCheckResult.status() == RiskCheckStatus.APPROVED ? HarnessStepStatus.COMPLETED : HarnessStepStatus.FAILED, riskCheckResult.reason()),
                new HarnessStepResult(HarnessStepType.EXECUTE_TRADE, tradeResult.status() == TradeStatus.REJECTED ? HarnessStepStatus.FAILED : HarnessStepStatus.COMPLETED, tradeResult.reason())
        );

        HarnessRunStatus runStatus = steps.stream()
                .anyMatch(step -> step.status() == HarnessStepStatus.FAILED)
                ? HarnessRunStatus.FAILED
                : HarnessRunStatus.COMPLETED;

        steps.forEach(step -> log.info("Harness step: {}", step));

        LocalDateTime finishedAt = LocalDateTime.now();

        log.info("Investment Harness finished.");

        return new HarnessRunResult(
                context.runId(),
                runStatus,
                startedAt,
                finishedAt,
                steps,
                decision,
                riskCheckResult,
                tradeResult,
                context.portfolioSnapshot(),
                context.marketSnapshot()
        );
    }
}
