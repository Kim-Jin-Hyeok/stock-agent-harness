package com.stock.harness.tool;

import com.stock.harness.execution.limit.HarnessProviderCallBudget;
import com.stock.market.MarketService;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceService;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.cache.CurrentPriceCache;
import com.stock.market.price.cache.CurrentPriceCacheProperties;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.provider.FixedCurrentPriceProvider;
import com.stock.market.price.provider.error.CurrentPriceProviderException;
import com.stock.market.price.provider.error.CurrentPriceProviderFailureType;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.market.price.validation.CurrentPriceFreshnessProperties;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarnessToolExecutorTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(1);

    @Test
    void executesPortfolioToolRequest() {
        HarnessProviderCallBudget budget = providerCallBudget(0);

        HarnessToolExecutionResult result = executor().execute(
                STRATEGY_IDENTITY,
                portfolioToolRequest(),
                budget
        );

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(portfolioTool());
        assertThat(result.reasonCode()).isEqualTo(toolExecutedReasonCode());
        assertThat(result.reason()).isEqualTo(toolExecutedReason());
        assertThat(result.output().type()).isEqualTo(portfolioTool());
        assertThat(result.output().portfolioSnapshot()).isEqualTo(portfolioSnapshot());
        assertThat(result.output().marketSnapshot()).isNull();
        assertThat(budget.usedCalls()).isZero();
    }

    @Test
    void executesMarketToolRequest() {
        HarnessToolExecutionResult result = executor().execute(
                STRATEGY_IDENTITY,
                marketToolRequest(),
                providerCallBudget(1)
        );

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(marketTool());
        assertThat(result.reasonCode()).isEqualTo(toolExecutedReasonCode());
        assertThat(result.reason()).isEqualTo(toolExecutedReason());
        assertThat(result.output().type()).isEqualTo(marketTool());
        assertThat(result.output().portfolioSnapshot()).isNull();
        assertThat(result.output().marketSnapshot()).isEqualTo(marketSnapshot());
    }

    @Test
    void executesCurrentPriceToolRequest() {
        HarnessProviderCallBudget budget = providerCallBudget(1);

        HarnessToolExecutionResult result = executor().execute(
                STRATEGY_IDENTITY,
                HarnessToolRequest.currentPrice("005930"),
                budget
        );

        assertThat(result.status()).isEqualTo(executedStatus());
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(result.request()).isEqualTo(HarnessToolRequest.currentPrice("005930"));
        assertThat(result.output().currentPriceSnapshot())
                .isEqualTo(new CurrentPriceSnapshot("005930", 100_000L, OBSERVED_AT));
        assertThat(result.output().currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(result.output().portfolioSnapshot()).isNull();
        assertThat(result.output().marketSnapshot()).isNull();
        assertThat(budget.usedCalls()).isEqualTo(1);
    }

    @Test
    void returnsFailedResultWhenToolServiceThrowsException() {
        CurrentPriceService failingService = mock(CurrentPriceService.class);
        when(failingService.getCurrentPrice(eq("005930"), any()))
                .thenThrow(new IllegalStateException("Broker timeout"));

        HarnessToolExecutionResult result = executor(failingService).execute(
                STRATEGY_IDENTITY,
                HarnessToolRequest.currentPrice("005930"),
                providerCallBudget(1)
        );

        assertThat(result.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
        assertThat(result.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(result.request()).isEqualTo(HarnessToolRequest.currentPrice("005930"));
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED
        );
        assertThat(result.reason()).isEqualTo("Tool execution failed. cause=Broker timeout");
        assertThat(result.output()).isNull();
    }

    @Test
    void returnsTemporaryFailureAndConsumesBudgetWhenProviderTemporarilyFails() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        when(provider.getCurrentPrice("005930")).thenThrow(
                new CurrentPriceProviderException(
                        CurrentPriceProviderFailureType.TEMPORARY,
                        "Broker timeout"
                )
        );
        HarnessProviderCallBudget budget = providerCallBudget(1);

        HarnessToolExecutionResult result = executor(
                currentPriceService(provider)
        ).execute(
                STRATEGY_IDENTITY,
                HarnessToolRequest.currentPrice("005930"),
                budget
        );

        assertThat(result.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
        assertThat(result.request()).isEqualTo(HarnessToolRequest.currentPrice("005930"));
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolExecutionReasonCode.PROVIDER_TEMPORARY_FAILURE
        );
        assertThat(result.reason()).isEqualTo(
                "Current price provider temporary failure. cause=Broker timeout"
        );
        assertThat(result.output()).isNull();
        assertThat(budget.usedCalls()).isEqualTo(1);
        verify(provider).getCurrentPrice("005930");
    }

    @Test
    void returnsPermanentFailureAndConsumesBudgetWhenProviderPermanentlyFails() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        when(provider.getCurrentPrice("005930")).thenThrow(
                new CurrentPriceProviderException(
                        CurrentPriceProviderFailureType.PERMANENT,
                        "Invalid authentication"
                )
        );
        HarnessProviderCallBudget budget = providerCallBudget(1);

        HarnessToolExecutionResult result = executor(
                currentPriceService(provider)
        ).execute(
                STRATEGY_IDENTITY,
                HarnessToolRequest.currentPrice("005930"),
                budget
        );

        assertThat(result.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
        assertThat(result.request()).isEqualTo(HarnessToolRequest.currentPrice("005930"));
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolExecutionReasonCode.PROVIDER_PERMANENT_FAILURE
        );
        assertThat(result.reason()).isEqualTo(
                "Current price provider permanent failure. cause=Invalid authentication"
        );
        assertThat(result.output()).isNull();
        assertThat(budget.usedCalls()).isEqualTo(1);
        verify(provider).getCurrentPrice("005930");
    }

    @Test
    void returnsFailedResultWithoutCallingProviderWhenProviderCallLimitIsExceeded() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        HarnessProviderCallBudget budget = providerCallBudget(0);
        HarnessToolRequest request = HarnessToolRequest.currentPrice("005930");
        CurrentPriceService currentPriceService = new CurrentPriceService(
                provider,
                new CurrentPriceCache(
                        new CurrentPriceCacheProperties(Duration.ofSeconds(30)),
                        Clock.fixed(OBSERVED_AT, ZoneOffset.UTC),
                        freshnessPolicy()
                ),
                freshnessPolicy()
        );

        HarnessToolExecutionResult result = executor(currentPriceService).execute(
                STRATEGY_IDENTITY,
                request,
                budget
        );

        assertThat(result.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(
                HarnessToolExecutionReasonCode.PROVIDER_CALL_LIMIT_EXCEEDED
        );
        assertThat(result.reason()).isEqualTo(
                "Provider call limit exceeded. used=0, max=0"
        );
        assertThat(result.output()).isNull();
        verify(provider, never()).getCurrentPrice("005930");
    }

    private HarnessToolExecutor executor() {
        return executor(currentPriceService());
    }

    private HarnessToolExecutor executor(CurrentPriceService currentPriceService) {
        return new HarnessToolExecutor(
                portfolioService(),
                marketService(),
                currentPriceService
        );
    }

    private CurrentPriceService currentPriceService() {
        return currentPriceService(
                new FixedCurrentPriceProvider(
                        Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
                )
        );
    }

    private CurrentPriceService currentPriceService(CurrentPriceProvider provider) {
        return new CurrentPriceService(
                provider,
                new CurrentPriceCache(
                        new CurrentPriceCacheProperties(Duration.ofSeconds(30)),
                        Clock.fixed(OBSERVED_AT, ZoneOffset.UTC),
                        freshnessPolicy()
                ),
                freshnessPolicy()
        );
    }

    private CurrentPriceFreshnessPolicy freshnessPolicy() {
        return new CurrentPriceFreshnessPolicy(
                new CurrentPriceFreshnessProperties(MAX_AGE),
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
    }

    private HarnessProviderCallBudget providerCallBudget(int maxCalls) {
        return new HarnessProviderCallBudget(maxCalls);
    }

    private PortfolioService portfolioService() {
        return new PortfolioService(portfolioSnapshotStore());
    }

    private PortfolioSnapshotStore portfolioSnapshotStore() {
        return new PortfolioSnapshotStore();
    }

    private MarketService marketService() {
        return new MarketService();
    }

    private HarnessToolRequest portfolioToolRequest() {
        return new HarnessToolRequest(portfolioTool());
    }

    private HarnessToolRequest marketToolRequest() {
        return new HarnessToolRequest(marketTool());
    }

    private HarnessToolType portfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolType marketTool() {
        return HarnessToolType.GET_MARKET;
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return portfolioSnapshotStore().getCurrentSnapshot(STRATEGY_IDENTITY);
    }

    private MarketSnapshot marketSnapshot() {
        return marketService().getCurrentSnapshot();
    }

    private HarnessToolExecutionStatus executedStatus() {
        return HarnessToolExecutionStatus.EXECUTED;
    }

    private HarnessToolExecutionReasonCode toolExecutedReasonCode() {
        return HarnessToolExecutionReasonCode.TOOL_EXECUTED;
    }

    private String toolExecutedReason() {
        return "Harness tool execution completed.";
    }
}
