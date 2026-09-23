package com.stock.trade.execution.virtual;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;

import static com.stock.portfolio.support.PortfolioSnapshotStoreFixture.create;
import static org.assertj.core.api.Assertions.assertThat;

class VirtualTradeExecutionHandlerTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final PortfolioSnapshotStore store = create();
    private final PortfolioService portfolioService = new PortfolioService(store);
    private final VirtualTradeExecutionHandler handler =
            new VirtualTradeExecutionHandler(portfolioService);

    @Test
    void buyIsExecutedAndAppliedToPortfolio() {
        InvestmentDecision decision = decision(InvestmentAction.BUY, 10L);

        TradeResult result = handler.execute(
                "run-1",
                STRATEGY_IDENTITY,
                decision
        );

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.reasonCode())
                .isEqualTo(TradeReasonCode.EXECUTION_COMPLETED);

        PortfolioSnapshot snapshot = portfolioService.getCurrentSnapshot(
                STRATEGY_IDENTITY
        );
        assertThat(snapshot.cashAmountKrw())
                .isEqualTo(10_000_000L - decision.estimatedOrderAmountKrw());
        assertThat(snapshot.positions())
                .singleElement()
                .satisfies(position -> {
                    assertThat(position.symbol()).isEqualTo("TEST");
                    assertThat(position.quantity()).isEqualTo(10L);
                });
    }

    @Test
    void sellIsExecutedAndAppliedToPortfolio() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                15L,
                50_000L
        );
        InvestmentDecision decision = decision(InvestmentAction.SELL, 10L);

        TradeResult result = handler.execute(
                "run-1",
                STRATEGY_IDENTITY,
                decision
        );

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.reasonCode())
                .isEqualTo(TradeReasonCode.EXECUTION_COMPLETED);

        PortfolioSnapshot snapshot = portfolioService.getCurrentSnapshot(
                STRATEGY_IDENTITY
        );
        assertThat(snapshot.positions())
                .singleElement()
                .satisfies(position -> {
                    assertThat(position.symbol()).isEqualTo("TEST");
                    assertThat(position.quantity()).isEqualTo(5L);
                });
    }

    private InvestmentDecision decision(
            InvestmentAction action,
            long quantity
    ) {
        return new InvestmentDecision(
                action,
                "TEST",
                quantity,
                100_000L,
                "Test trade decision."
        );
    }
}
