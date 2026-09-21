package com.stock.harness;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.agent.validation.HarnessAgentActionValidationReasonCode;
import com.stock.harness.agent.validation.HarnessAgentActionValidator;
import com.stock.harness.execution.retry.HarnessRetryWaiter;
import com.stock.harness.persistence.HarnessRunRepository;
import com.stock.harness.persistence.HarnessRunSnapshotJsonConverter;
import com.stock.harness.persistence.HarnessStepRepository;
import com.stock.harness.tool.HarnessToolAuthorizationReasonCode;
import com.stock.harness.tool.HarnessToolAuthorizationResult;
import com.stock.harness.tool.HarnessToolAuthorizer;
import com.stock.harness.tool.HarnessToolExecutor;
import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.harness.tool.validation.HarnessToolRequestValidationReasonCode;
import com.stock.harness.tool.validation.HarnessToolRequestValidator;
import com.stock.harness.tool.validation.HarnessToolResultValidationReasonCode;
import com.stock.harness.tool.validation.HarnessToolResultValidator;
import com.stock.market.MarketService;
import com.stock.market.price.CurrentPriceService;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.cache.CurrentPriceCache;
import com.stock.market.price.cache.CurrentPriceCacheProperties;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.provider.FixedCurrentPriceProvider;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.market.price.validation.CurrentPriceFreshnessProperties;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.risk.RiskProperties;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeStatus;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvestmentHarnessTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private static final Instant CURRENT_PRICE_OBSERVED_AT = Instant.parse(
            "2026-01-01T00:00:00Z"
    );
    private static final Duration CURRENT_PRICE_MAX_AGE = Duration.ofMinutes(1);

    private final HarnessProperties harnessProperties = new HarnessProperties(
            10,
            5
    );

    private final RiskProperties riskProperties = new RiskProperties(
            0.1,
            0.3
    );

    private final HarnessRunRepository harnessRunRepository = mock(HarnessRunRepository.class);
    private final HarnessRunSnapshotJsonConverter harnessRunSnapshotJsonConverter =
            mock(HarnessRunSnapshotJsonConverter.class);
    private final HarnessStepRepository harnessStepRepository = mock(HarnessStepRepository.class);
    private final RiskGuard riskGuard = new RiskGuard(riskProperties);
    private final PortfolioSnapshotStore store = new PortfolioSnapshotStore();
    private final PortfolioService portfolioService = new PortfolioService(store);
    private final TradeHistoryService tradeHistoryService = new TradeHistoryService(mock(TradeRecordRepository.class));
    private final TradeExecutor tradeExecutor = new TradeExecutor(
            portfolioService,
            tradeHistoryService
    );
    private final MarketService marketService = new MarketService();
    private final CurrentPriceFreshnessPolicy currentPriceFreshnessPolicy =
            new CurrentPriceFreshnessPolicy(
                    new CurrentPriceFreshnessProperties(CURRENT_PRICE_MAX_AGE),
                    Clock.fixed(CURRENT_PRICE_OBSERVED_AT, ZoneOffset.UTC)
            );
    private final CurrentPriceService currentPriceService = new CurrentPriceService(
            new FixedCurrentPriceProvider(
                    Clock.fixed(CURRENT_PRICE_OBSERVED_AT, ZoneOffset.UTC)
            ),
            new CurrentPriceCache(
                    new CurrentPriceCacheProperties(Duration.ofSeconds(30)),
                    Clock.fixed(CURRENT_PRICE_OBSERVED_AT, ZoneOffset.UTC),
                    currentPriceFreshnessPolicy
            ),
            currentPriceFreshnessPolicy
    );
    private final HarnessRunHistoryService harnessRunHistoryService = new HarnessRunHistoryService(
            harnessRunSnapshotJsonConverter,
            harnessRunRepository,
            harnessStepRepository
    );
    private final InvestmentAgent investmentAgent = new InvestmentAgent();
    private final HarnessToolAuthorizer harnessToolAuthorizer = new HarnessToolAuthorizer();
    private final HarnessToolExecutor harnessToolExecutor = new HarnessToolExecutor(
            portfolioService,
            marketService,
            currentPriceService
    );
    private final HarnessToolResultValidator harnessToolResultValidator =
            new HarnessToolResultValidator(currentPriceFreshnessPolicy);
    private final HarnessAgentActionValidator harnessAgentActionValidator = new HarnessAgentActionValidator();
    private final HarnessToolRequestValidator harnessToolRequestValidator = new HarnessToolRequestValidator();
    private final HarnessRetryWaiter harnessRetryWaiter = mock(HarnessRetryWaiter.class);

    @BeforeEach
    void setUpRetryWaiter() {
        when(harnessRetryWaiter.waitBeforeRetry()).thenReturn(Duration.ofMillis(500));
    }

    private final InvestmentHarness investmentHarness = new InvestmentHarness(
            riskGuard,
            tradeExecutor,
            portfolioService,
            marketService,
            harnessRunHistoryService,
            investmentAgent,
            harnessProperties,
            harnessToolAuthorizer,
            harnessToolExecutor,
            harnessToolResultValidator,
            harnessAgentActionValidator,
            harnessToolRequestValidator,
            harnessRetryWaiter
    );

    @Test
    void runCompletesWithHoldDecision() {
        HarnessRunResult result = investmentHarness.run(STRATEGY_IDENTITY);

        assertThat(result.strategyIdentity()).isEqualTo(STRATEGY_IDENTITY);
        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.tradeResult().status()).isEqualTo(TradeStatus.SKIPPED);
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.steps().size()).isEqualTo(8);

        List<HarnessStepType> stepTypes = result.steps().stream()
                .map(HarnessStepResult::type)
                .toList();

        assertThat(stepTypes).containsExactly(
                HarnessStepType.LOAD_PORTFOLIO,
                HarnessStepType.LOAD_MARKET,
                HarnessStepType.CHECK_STEP_LIMIT,
                HarnessStepType.RUN_INVESTMENT_AGENT,
                HarnessStepType.VALIDATE_AGENT_ACTION,
                HarnessStepType.VALIDATE_DECISION,
                HarnessStepType.EXECUTE_TRADE,
                HarnessStepType.LOAD_FINAL_PORTFOLIO
        );

        HarnessStepResult loadPortfolioStep = result.steps().get(0);
        HarnessStepResult loadMarketStep = result.steps().get(1);

        assertThat(loadPortfolioStep.startedAt()).isNotNull();
        assertThat(loadPortfolioStep.finishedAt()).isNotNull();
        assertThat(loadPortfolioStep.startedAt()).isBeforeOrEqualTo(loadPortfolioStep.finishedAt());

        assertThat(loadMarketStep.startedAt()).isNotNull();
        assertThat(loadMarketStep.finishedAt()).isNotNull();
        assertThat(loadMarketStep.startedAt()).isBeforeOrEqualTo(loadMarketStep.finishedAt());

        HarnessStepResult agentStep = result.steps().get(3);

        assertThat(agentStep.type()).isEqualTo(HarnessStepType.RUN_INVESTMENT_AGENT);
        assertThat(agentStep.message())
                .contains("allowedTools=[GET_PORTFOLIO, GET_MARKET, GET_CURRENT_PRICE]");

        HarnessStepResult validateActionStep = result.steps().get(4);

        assertThat(validateActionStep.type()).isEqualTo(HarnessStepType.VALIDATE_AGENT_ACTION);
        assertThat(validateActionStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(validateActionStep.message()).isEqualTo("Harness agent action is valid.");

        HarnessStepResult validateDecisionStep = result.steps().get(5);

        assertThat(validateDecisionStep.type()).isEqualTo(HarnessStepType.VALIDATE_DECISION);
        assertThat(validateDecisionStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(validateDecisionStep.message()).isEqualTo("HOLD decision does not require order risk validation.");
        assertThat(validateDecisionStep.startedAt()).isNotNull();
        assertThat(validateDecisionStep.finishedAt()).isNotNull();
        assertThat(validateDecisionStep.startedAt()).isBeforeOrEqualTo(validateDecisionStep.finishedAt());

        HarnessStepResult executeTradeStep = result.steps().get(6);

        assertThat(executeTradeStep.type()).isEqualTo(HarnessStepType.EXECUTE_TRADE);
        assertThat(executeTradeStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(executeTradeStep.message()).isEqualTo("HOLD decision does not create an order.");
        assertThat(executeTradeStep.startedAt()).isNotNull();
        assertThat(executeTradeStep.finishedAt()).isNotNull();
        assertThat(executeTradeStep.startedAt()).isBeforeOrEqualTo(executeTradeStep.finishedAt());

        HarnessStepResult finalPortfolioStep = result.steps().get(7);

        assertThat(finalPortfolioStep.type()).isEqualTo(HarnessStepType.LOAD_FINAL_PORTFOLIO);
        assertThat(finalPortfolioStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(finalPortfolioStep.message()).isEqualTo("Final portfolio loading complete.");
        assertThat(finalPortfolioStep.startedAt()).isNotNull();
        assertThat(finalPortfolioStep.finishedAt()).isNotNull();
        assertThat(finalPortfolioStep.startedAt()).isBeforeOrEqualTo(finalPortfolioStep.finishedAt());
    }

    @Test
    void runFailsBeforeSecondAgentCallWhenAgentStepLimitExceeded() {
        InvestmentHarness limitedHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new ToolResultUsingInvestmentAgent(),
                new HarnessProperties(1, 1),
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = limitedHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.VALIDATE_TOOL_RESULT,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_FAILED
                );

        HarnessStepResult failedLimitStep = result.steps().get(11);

        assertThat(failedLimitStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(failedLimitStep.message()).isEqualTo("Agent step limit exceeded. used=1, max=1");
        assertThat(result.toolResults())
                .extracting(HarnessToolExecutionResult::type)
                .containsExactly(HarnessToolType.GET_PORTFOLIO);
    }

    @Test
    void runFailsWhenAgentDecisionThrowsException() {
        InvestmentHarness failingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new FailingInvestmentAgent(),
                harnessProperties,
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = failingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.RUN_FAILED
                );
        HarnessStepResult agentStep = result.steps().get(3);

        assertThat(agentStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(agentStep.message()).isEqualTo("Test agent failure");
        assertThat(agentStep.startedAt()).isNotNull();
        assertThat(agentStep.finishedAt()).isNotNull();
        assertThat(agentStep.startedAt()).isBeforeOrEqualTo(agentStep.finishedAt());

        assertThat(result.steps().getLast().status()).isEqualTo(HarnessStepStatus.FAILED);
    }

    @Test
    void runRejectsNullStrategyIdentity() {
        assertThatThrownBy(() -> investmentHarness.run(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("strategyIdentity must not be null.");
    }

    @Test
    void runFailsBeforeToolExecutionWhenAgentReturnsInvalidToolRequestAction() {
        InvestmentAgent invalidActionAgent = mock(InvestmentAgent.class);
        when(invalidActionAgent.next(any())).thenReturn(new AgentNextAction(
                AgentNextActionType.REQUEST_TOOL,
                null,
                null
        ));
        HarnessToolExecutor unusedToolExecutor = mock(HarnessToolExecutor.class);
        InvestmentHarness validatingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                invalidActionAgent,
                harnessProperties,
                harnessToolAuthorizer,
                unusedToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = validatingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.RUN_FAILED
                );
        assertThat(result.steps().get(4).status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(result.steps().get(4).message()).isEqualTo(
                "Tool request is missing for REQUEST_TOOL action."
        );
        assertThat(result.steps().getLast().message()).contains(
                HarnessAgentActionValidationReasonCode.TOOL_REQUEST_MISSING.name()
        );
        verify(unusedToolExecutor, never()).execute(any(), any());
    }

    @Test
    void runFailsBeforeAuthorizationWhenToolRequestTypeIsMissing() {
        InvestmentAgent invalidRequestAgent = mock(InvestmentAgent.class);
        when(invalidRequestAgent.next(any())).thenReturn(
                AgentNextAction.requestTool(new HarnessToolRequest(null))
        );
        HarnessToolAuthorizer unusedAuthorizer = mock(HarnessToolAuthorizer.class);
        HarnessToolExecutor unusedToolExecutor = mock(HarnessToolExecutor.class);
        InvestmentHarness validatingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                invalidRequestAgent,
                harnessProperties,
                unusedAuthorizer,
                unusedToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = validatingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.RUN_FAILED
                );
        assertThat(result.steps().get(5).status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(result.steps().get(5).message()).isEqualTo(
                "Tool request type is missing."
        );
        assertThat(result.steps().getLast().message()).contains(
                HarnessToolRequestValidationReasonCode.TOOL_TYPE_MISSING.name()
        );
        verify(unusedAuthorizer, never()).authorize(any(), any());
        verify(unusedToolExecutor, never()).execute(any(), any());
    }

    @Test
    void runCompletesWhenAgentDecidesBuy() {
        InvestmentHarness successHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new BuyingInvestmentAgent(),
                harnessProperties,
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = successHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.BUY);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.tradeResult().status()).isEqualTo(TradeStatus.EXECUTED);

        boolean hasCompletedExecuteTradeStep = result.steps().stream()
                .anyMatch(harnessStepResult ->
                        harnessStepResult.type().equals(HarnessStepType.EXECUTE_TRADE)
                        && harnessStepResult.status().equals(HarnessStepStatus.COMPLETED)
                );
        assertThat(hasCompletedExecuteTradeStep).isTrue();

        HarnessStepResult executeTradeStep = result.steps().get(6);

        assertThat(executeTradeStep.type()).isEqualTo(HarnessStepType.EXECUTE_TRADE);
        assertThat(executeTradeStep.message()).isEqualTo("BUY execution is complete.");
        assertThat(executeTradeStep.startedAt()).isNotNull();
        assertThat(executeTradeStep.finishedAt()).isNotNull();
        assertThat(executeTradeStep.startedAt()).isBeforeOrEqualTo(executeTradeStep.finishedAt());

        boolean hasBuyingSymbol = result.portfolioSnapshot().positions().stream()
                .anyMatch(position -> position.symbol().equals(result.decision().symbol()));
        assertThat(hasBuyingSymbol).isTrue();
    }

    @Test
    void runFailsWhenRiskGuardDeniesDecision() {
        InvestmentHarness deniedHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new OverLimitBuyingInvestmentAgent(),
                harnessProperties,
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = deniedHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.DENIED);

        HarnessStepResult validateDecisionStep = result.steps().get(5);

        assertThat(validateDecisionStep.type()).isEqualTo(HarnessStepType.VALIDATE_DECISION);
        assertThat(validateDecisionStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(validateDecisionStep.message()).contains("Order amount exceeds max order ratio.");
        assertThat(validateDecisionStep.startedAt()).isNotNull();
        assertThat(validateDecisionStep.finishedAt()).isNotNull();
        assertThat(validateDecisionStep.startedAt()).isBeforeOrEqualTo(validateDecisionStep.finishedAt());

        HarnessStepResult executeTradeStep = result.steps().get(6);

        assertThat(executeTradeStep.type()).isEqualTo(HarnessStepType.EXECUTE_TRADE);
        assertThat(executeTradeStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(executeTradeStep.message()).isEqualTo("Risk check denied the decision.");
        assertThat(executeTradeStep.startedAt()).isNotNull();
        assertThat(executeTradeStep.finishedAt()).isNotNull();
        assertThat(executeTradeStep.startedAt()).isBeforeOrEqualTo(executeTradeStep.finishedAt());
    }

    @Test
    void runCompletesWhenAgentDecidesSell() {
        PortfolioService sellPortfolioService = new PortfolioService(store);
        sellPortfolioService.applyBuy(
                "TEST",
                10L,
                100_000L
        );

        TradeExecutor sellTradeExecutor = new TradeExecutor(
                sellPortfolioService,
                tradeHistoryService
        );

        InvestmentHarness sellHarness = new InvestmentHarness(
                riskGuard,
                sellTradeExecutor,
                sellPortfolioService,
                marketService,
                harnessRunHistoryService,
                new SellingInvestmentAgent(),
                harnessProperties,
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = sellHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.SELL);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.tradeResult().status()).isEqualTo(TradeStatus.EXECUTED);

        PortfolioPosition position = result.portfolioSnapshot().positions().getFirst();

        assertThat(position.symbol()).isEqualTo("TEST");
        assertThat(position.quantity()).isEqualTo(5L);
    }

    @Test
    void runCompletesAfterAgentUsesPortfolioToolResult() {
        InvestmentHarness toolRequestingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new ToolResultUsingInvestmentAgent(),
                new HarnessProperties(2, 1),
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = toolRequestingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.decision().reason()).isEqualTo(
                "Portfolio tool result received. cashAmountKrw="
                + result.portfolioSnapshot().cashAmountKrw()
        );
        assertThat(result.toolResults())
                .singleElement()
                .satisfies(toolResult -> {
                    assertThat(toolResult.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
                    assertThat(toolResult.output().portfolioSnapshot()).isNotNull();
                });
        assertThatThrownBy(() -> result.toolResults().add(result.toolResults().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.VALIDATE_TOOL_RESULT,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_DECISION,
                        HarnessStepType.EXECUTE_TRADE,
                        HarnessStepType.LOAD_FINAL_PORTFOLIO
                );

        HarnessStepResult firstLimitStep = result.steps().get(2);

        assertThat(firstLimitStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(firstLimitStep.message()).isEqualTo("Agent step allowed. used=1, max=2");

        HarnessStepResult agentStep = result.steps().get(3);

        assertThat(agentStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(agentStep.message()).isEqualTo(
                "Requested tool. type=GET_PORTFOLIO"
        );

        HarnessStepResult authorizationStep = result.steps().get(6);

        assertThat(authorizationStep.type()).isEqualTo(HarnessStepType.AUTHORIZE_TOOL_REQUEST);
        assertThat(authorizationStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(authorizationStep.message()).isEqualTo("Harness tool authorization allowed.");

        HarnessStepResult duplicateCheckStep = result.steps().get(7);

        assertThat(duplicateCheckStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(duplicateCheckStep.message()).isEqualTo(
                "Tool request is not duplicate. type=GET_PORTFOLIO"
        );

        HarnessStepResult toolLimitStep = result.steps().get(8);

        assertThat(toolLimitStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(toolLimitStep.message()).isEqualTo("Tool call allowed. used=1, max=1");

        HarnessStepResult executeToolStep = result.steps().get(9);

        assertThat(executeToolStep.type()).isEqualTo(HarnessStepType.EXECUTE_TOOL_REQUEST);
        assertThat(executeToolStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(executeToolStep.message()).isEqualTo("Harness tool execution completed.");

        HarnessStepResult validationStep = result.steps().get(10);

        assertThat(validationStep.type()).isEqualTo(HarnessStepType.VALIDATE_TOOL_RESULT);
        assertThat(validationStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(validationStep.message()).isEqualTo("Harness tool result is valid.");

        HarnessStepResult secondLimitStep = result.steps().get(11);

        assertThat(secondLimitStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(secondLimitStep.message()).isEqualTo("Agent step allowed. used=2, max=2");

        HarnessStepResult secondAgentStep = result.steps().get(12);

        assertThat(secondAgentStep.type()).isEqualTo(HarnessStepType.RUN_INVESTMENT_AGENT);
        assertThat(secondAgentStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(secondAgentStep.message()).isEqualTo(result.decision().reason());
    }

    @Test
    void runCompletesAfterAgentUsesMultipleToolResults() {
        InvestmentHarness multipleToolHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new MultipleToolResultUsingInvestmentAgent(),
                new HarnessProperties(3, 2),
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = multipleToolHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.decision().reason()).isEqualTo(
                "Multiple tool results received. cashAmountKrw="
                + result.portfolioSnapshot().cashAmountKrw()
                + ", market="
                + result.marketSnapshot().market()
        );
        assertThat(result.toolResults())
                .extracting(HarnessToolExecutionResult::type)
                .containsExactly(
                        HarnessToolType.GET_PORTFOLIO,
                        HarnessToolType.GET_MARKET
                );
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.VALIDATE_TOOL_RESULT,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.VALIDATE_TOOL_RESULT,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_DECISION,
                        HarnessStepType.EXECUTE_TRADE,
                        HarnessStepType.LOAD_FINAL_PORTFOLIO
                );
    }

    @Test
    void runCompletesAfterAgentUsesCurrentPriceResult() {
        InvestmentHarness currentPriceHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new CurrentPriceUsingInvestmentAgent(),
                new HarnessProperties(2, 1),
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = currentPriceHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().reason()).isEqualTo(
                "Current price received. symbol=005930, priceKrw=100000"
        );
        assertThat(result.toolResults())
                .singleElement()
                .satisfies(toolResult -> {
                    assertThat(toolResult.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
                    assertThat(toolResult.output().currentPriceSnapshot().symbol())
                            .isEqualTo("005930");
                    assertThat(toolResult.output().currentPriceSnapshot().priceKrw())
                            .isEqualTo(100_000L);
                });
    }

    @Test
    void runFailsBeforeSecondToolExecutionWhenToolCallLimitExceeded() {
        InvestmentHarness limitedToolHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new MultipleToolResultUsingInvestmentAgent(),
                new HarnessProperties(3, 1),
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = limitedToolHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.VALIDATE_TOOL_RESULT,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.RUN_FAILED
                );

        HarnessStepResult failedToolLimitStep = result.steps().get(17);

        assertThat(failedToolLimitStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(failedToolLimitStep.message()).isEqualTo(
                "Tool call limit exceeded. used=1, max=1"
        );
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.EXECUTE_TOOL_REQUEST::equals)
                .hasSize(1);
        assertThat(result.toolResults())
                .extracting(HarnessToolExecutionResult::type)
                .containsExactly(HarnessToolType.GET_PORTFOLIO);
    }

    @Test
    void runPreservesAuthorizationDeniedToolResult() {
        HarnessToolAuthorizer deniedAuthorizer = mock(HarnessToolAuthorizer.class);
        when(deniedAuthorizer.authorize(any(), any()))
                .thenReturn(HarnessToolAuthorizationResult.denied(
                        HarnessToolType.GET_PORTFOLIO,
                        HarnessToolAuthorizationReasonCode.TOOL_NOT_ALLOWED,
                        "Harness tool is not allowed."
                ));
        InvestmentHarness deniedHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new ToolResultUsingInvestmentAgent(),
                harnessProperties,
                deniedAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = deniedHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults())
                .singleElement()
                .satisfies(toolResult -> {
                    assertThat(toolResult.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
                    assertThat(toolResult.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
                    assertThat(toolResult.request()).isEqualTo(HarnessToolRequest.portfolio());
                    assertThat(toolResult.reasonCode()).isEqualTo(
                            HarnessToolExecutionReasonCode.TOOL_AUTHORIZATION_DENIED
                    );
                    assertThat(toolResult.output()).isNull();
                });
    }

    @Test
    void runPreservesFailedToolExecutionResult() {
        HarnessToolExecutor failingToolExecutor = mock(HarnessToolExecutor.class);
        when(failingToolExecutor.execute(any(), any()))
                .thenReturn(HarnessToolExecutionResult.notSupported(
                        HarnessToolType.GET_PORTFOLIO
                ));
        InvestmentHarness failingToolHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new ToolResultUsingInvestmentAgent(),
                harnessProperties,
                harnessToolAuthorizer,
                failingToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = failingToolHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults())
                .singleElement()
                .satisfies(toolResult -> {
                    assertThat(toolResult.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
                    assertThat(toolResult.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
                    assertThat(toolResult.reasonCode()).isEqualTo(
                            HarnessToolExecutionReasonCode.TOOL_NOT_SUPPORTED
                    );
                    assertThat(toolResult.output()).isNull();
                });
    }

    @Test
    void runRetriesFailedToolExecutionAndCompletesAfterSuccess() {
        HarnessToolExecutor retryingExecutor = mock(HarnessToolExecutor.class);
        when(retryingExecutor.execute(any(), any()))
                .thenReturn(
                        HarnessToolExecutionResult.executionFailed(
                                HarnessToolRequest.portfolio(),
                                "Broker timeout"
                        ),
                        HarnessToolExecutionResult.executed(
                                HarnessToolRequest.portfolio(),
                                HarnessToolOutput.portfolio(portfolioService.getCurrentSnapshot())
                        )
                );
        InvestmentHarness retryingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new ToolResultUsingInvestmentAgent(),
                new HarnessProperties(2, 2, 1),
                harnessToolAuthorizer,
                retryingExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = retryingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.toolResults())
                .extracting(
                        HarnessToolExecutionResult::status,
                        HarnessToolExecutionResult::reasonCode
                )
                .containsExactly(
                        tuple(
                                HarnessToolExecutionStatus.FAILED,
                                HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED
                        ),
                        tuple(
                                HarnessToolExecutionStatus.EXECUTED,
                                HarnessToolExecutionReasonCode.TOOL_EXECUTED
                        )
                );
        assertThat(result.toolResults())
                .extracting(HarnessToolExecutionResult::request)
                .containsExactly(
                        HarnessToolRequest.portfolio(),
                        HarnessToolRequest.portfolio()
                );
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.CHECK_TOOL_CALL_LIMIT::equals)
                .hasSize(2);
        assertThat(result.steps())
                .filteredOn(step -> step.type() == HarnessStepType.EXECUTE_TOOL_REQUEST)
                .extracting(HarnessStepResult::status)
                .containsExactly(
                        HarnessStepStatus.FAILED,
                        HarnessStepStatus.COMPLETED
                );
        assertThat(result.steps())
                .filteredOn(step -> step.type() == HarnessStepType.WAIT_TOOL_RETRY)
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.status()).isEqualTo(HarnessStepStatus.COMPLETED);
                    assertThat(step.message()).isEqualTo(
                            "Tool retry wait completed. delay=PT0.5S"
                    );
                });
        verify(retryingExecutor, times(2)).execute(any(), any());
        verify(harnessRetryWaiter).waitBeforeRetry();
    }

    @Test
    void runRetriesTemporaryProviderFailureAndCompletesAfterSuccess() {
        HarnessToolRequest request = HarnessToolRequest.currentPrice("005930");
        HarnessToolExecutor retryingExecutor = mock(HarnessToolExecutor.class);
        when(retryingExecutor.execute(any(), any()))
                .thenReturn(
                        HarnessToolExecutionResult.providerTemporaryFailure(
                                request,
                                "Broker timeout"
                        ),
                        HarnessToolExecutionResult.executed(
                                request,
                                HarnessToolOutput.currentPrice(new CurrentPriceSnapshot(
                                        "005930",
                                        100_000L,
                                        CURRENT_PRICE_OBSERVED_AT
                                ))
                        )
                );
        InvestmentHarness retryingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new CurrentPriceUsingInvestmentAgent(),
                new HarnessProperties(2, 2, 1),
                harnessToolAuthorizer,
                retryingExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = retryingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.toolResults())
                .extracting(
                        HarnessToolExecutionResult::status,
                        HarnessToolExecutionResult::reasonCode
                )
                .containsExactly(
                        tuple(
                                HarnessToolExecutionStatus.FAILED,
                                HarnessToolExecutionReasonCode.PROVIDER_TEMPORARY_FAILURE
                        ),
                        tuple(
                                HarnessToolExecutionStatus.EXECUTED,
                                HarnessToolExecutionReasonCode.TOOL_EXECUTED
                        )
                );
        assertThat(result.toolResults())
                .extracting(HarnessToolExecutionResult::request)
                .containsExactly(request, request);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.RUN_INVESTMENT_AGENT::equals)
                .hasSize(2);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.VALIDATE_TOOL_RESULT::equals)
                .hasSize(1);
        assertThat(result.steps())
                .filteredOn(step -> step.type() == HarnessStepType.EXECUTE_TOOL_REQUEST)
                .extracting(HarnessStepResult::status)
                .containsExactly(
                        HarnessStepStatus.FAILED,
                        HarnessStepStatus.COMPLETED
                );
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsSubsequence(
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.WAIT_TOOL_RETRY,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST
                );
        assertThat(result.steps())
                .filteredOn(step -> step.type() == HarnessStepType.WAIT_TOOL_RETRY)
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.status()).isEqualTo(HarnessStepStatus.COMPLETED);
                    assertThat(step.message()).isEqualTo(
                            "Tool retry wait completed. delay=PT0.5S"
                    );
                });
        verify(retryingExecutor, times(2)).execute(any(), any());
        verify(harnessRetryWaiter).waitBeforeRetry();
    }

    @Test
    void runDoesNotRetryPermanentProviderFailure() {
        HarnessToolRequest request = HarnessToolRequest.currentPrice("005930");
        InvestmentAgent requestingAgent = mock(InvestmentAgent.class);
        when(requestingAgent.next(any())).thenReturn(AgentNextAction.requestTool(request));
        HarnessToolExecutor failingExecutor = mock(HarnessToolExecutor.class);
        when(failingExecutor.execute(any(), any())).thenReturn(
                HarnessToolExecutionResult.providerPermanentFailure(
                        request,
                        "Invalid authentication"
                )
        );
        InvestmentHarness failingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                requestingAgent,
                new HarnessProperties(2, 2, 1),
                harnessToolAuthorizer,
                failingExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = failingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.strategyIdentity()).isEqualTo(STRATEGY_IDENTITY);
        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults())
                .singleElement()
                .satisfies(toolResult -> {
                    assertThat(toolResult.status()).isEqualTo(
                            HarnessToolExecutionStatus.FAILED
                    );
                    assertThat(toolResult.reasonCode()).isEqualTo(
                            HarnessToolExecutionReasonCode.PROVIDER_PERMANENT_FAILURE
                    );
                    assertThat(toolResult.request()).isEqualTo(request);
                });
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.RUN_INVESTMENT_AGENT::equals)
                .hasSize(1);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .doesNotContain(
                        HarnessStepType.WAIT_TOOL_RETRY,
                        HarnessStepType.VALIDATE_TOOL_RESULT
                );
        verify(failingExecutor).execute(any(), any());
        verify(requestingAgent).next(any());
        verifyNoInteractions(harnessRetryWaiter);
    }

    @Test
    void runRecordsFailedRetryWaitStepWhenWaitIsInterrupted() {
        HarnessToolRequest request = HarnessToolRequest.portfolio();
        InvestmentAgent requestingAgent = mock(InvestmentAgent.class);
        when(requestingAgent.next(any())).thenReturn(AgentNextAction.requestTool(request));
        HarnessToolExecutor failingExecutor = mock(HarnessToolExecutor.class);
        when(failingExecutor.execute(any(), any())).thenReturn(
                HarnessToolExecutionResult.executionFailed(request, "Broker timeout")
        );
        when(harnessRetryWaiter.waitBeforeRetry()).thenThrow(
                new IllegalStateException("Tool retry wait interrupted.")
        );
        InvestmentHarness interruptedHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                requestingAgent,
                new HarnessProperties(2, 2, 1),
                harnessToolAuthorizer,
                failingExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = interruptedHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults())
                .singleElement()
                .satisfies(toolResult -> assertThat(toolResult.reasonCode()).isEqualTo(
                        HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED
                ));
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsSubsequence(
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.WAIT_TOOL_RETRY,
                        HarnessStepType.RUN_FAILED
                );
        assertThat(result.steps())
                .filteredOn(step -> step.type() == HarnessStepType.WAIT_TOOL_RETRY)
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.status()).isEqualTo(HarnessStepStatus.FAILED);
                    assertThat(step.message()).isEqualTo("Tool retry wait interrupted.");
                });
        verify(failingExecutor).execute(any(), any());
        verify(harnessRetryWaiter).waitBeforeRetry();
        verify(requestingAgent).next(any());
    }

    @Test
    void runFailsAfterToolRetriesAreExhausted() {
        InvestmentAgent requestingAgent = mock(InvestmentAgent.class);
        when(requestingAgent.next(any())).thenReturn(
                AgentNextAction.requestTool(HarnessToolRequest.portfolio())
        );
        HarnessToolExecutor failingExecutor = mock(HarnessToolExecutor.class);
        when(failingExecutor.execute(any(), any())).thenReturn(
                HarnessToolExecutionResult.executionFailed(
                        HarnessToolRequest.portfolio(),
                        "Broker timeout"
                )
        );
        InvestmentHarness retryingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                requestingAgent,
                new HarnessProperties(2, 2, 1),
                harnessToolAuthorizer,
                failingExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = retryingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults())
                .hasSize(2)
                .allSatisfy(toolResult -> {
                    assertThat(toolResult.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
                    assertThat(toolResult.reasonCode()).isEqualTo(
                            HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED
                    );
                });
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.CHECK_TOOL_CALL_LIMIT::equals)
                .hasSize(2);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.EXECUTE_TOOL_REQUEST::equals)
                .hasSize(2);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.WAIT_TOOL_RETRY::equals)
                .hasSize(1);
        verify(failingExecutor, times(2)).execute(any(), any());
        verify(requestingAgent).next(any());
        verify(harnessRetryWaiter).waitBeforeRetry();
    }

    @Test
    void runStopsRetryingWhenProviderCallBudgetIsExhausted() {
        CurrentPriceProvider provider = mock(CurrentPriceProvider.class);
        when(provider.getCurrentPrice("005930"))
                .thenThrow(new IllegalStateException("Broker timeout"));
        CurrentPriceService failingCurrentPriceService = new CurrentPriceService(
                provider,
                new CurrentPriceCache(
                        new CurrentPriceCacheProperties(Duration.ofSeconds(30)),
                        Clock.fixed(CURRENT_PRICE_OBSERVED_AT, ZoneOffset.UTC),
                        currentPriceFreshnessPolicy
                ),
                currentPriceFreshnessPolicy
        );
        HarnessToolExecutor providerLimitedExecutor = new HarnessToolExecutor(
                portfolioService,
                marketService,
                failingCurrentPriceService
        );
        InvestmentHarness providerLimitedHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new CurrentPriceUsingInvestmentAgent(),
                new HarnessProperties(2, 3, 2, 1),
                harnessToolAuthorizer,
                providerLimitedExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = providerLimitedHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults())
                .extracting(HarnessToolExecutionResult::reasonCode)
                .containsExactly(
                        HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED,
                        HarnessToolExecutionReasonCode.PROVIDER_CALL_LIMIT_EXCEEDED
                );
        assertThat(result.toolResults().getLast().reason()).isEqualTo(
                "Provider call limit exceeded. used=1, max=1"
        );
        verify(provider).getCurrentPrice("005930");
    }

    @Test
    void runFailsWithoutRerunningAgentWhenToolResultValidationFails() {
        InvestmentAgent requestingAgent = mock(InvestmentAgent.class);
        when(requestingAgent.next(any()))
                .thenReturn(AgentNextAction.requestTool(
                        new HarnessToolRequest(HarnessToolType.GET_PORTFOLIO)
                ));
        HarnessToolExecutionResult malformedResult = new HarnessToolExecutionResult(
                HarnessToolExecutionStatus.EXECUTED,
                HarnessToolType.GET_PORTFOLIO,
                HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                "Harness tool execution completed.",
                new HarnessToolOutput(
                        HarnessToolType.GET_PORTFOLIO,
                        null,
                        null
                )
        );
        HarnessToolExecutor malformedExecutor = mock(HarnessToolExecutor.class);
        when(malformedExecutor.execute(any(), any())).thenReturn(malformedResult);
        InvestmentHarness validatingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                requestingAgent,
                harnessProperties,
                harnessToolAuthorizer,
                malformedExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = validatingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.toolResults()).containsExactly(malformedResult);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.VALIDATE_TOOL_RESULT,
                        HarnessStepType.RUN_FAILED
                );

        HarnessStepResult validationStep = result.steps().get(10);
        assertThat(validationStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(validationStep.message()).isEqualTo(
                "Expected tool output payload is missing. type=GET_PORTFOLIO"
        );
        assertThat(result.steps().getLast().message()).contains(
                HarnessToolResultValidationReasonCode.OUTPUT_PAYLOAD_MISSING.name()
        );
        verify(requestingAgent).next(any());
    }

    @Test
    void runFailsBeforeRepeatedToolExecutionWhenAgentRequestsDuplicateTool() {
        InvestmentHarness toolRequestingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new ToolRequestingInvestmentAgent(),
                new HarnessProperties(2, 2),
                harnessToolAuthorizer,
                harnessToolExecutor,
                harnessToolResultValidator,
                harnessAgentActionValidator,
                harnessToolRequestValidator,
                harnessRetryWaiter
        );

        HarnessRunResult result = toolRequestingHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                        HarnessStepType.EXECUTE_TOOL_REQUEST,
                        HarnessStepType.VALIDATE_TOOL_RESULT,
                        HarnessStepType.CHECK_STEP_LIMIT,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.VALIDATE_AGENT_ACTION,
                        HarnessStepType.VALIDATE_TOOL_REQUEST,
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                        HarnessStepType.RUN_FAILED
                );

        HarnessStepResult duplicateCheckStep = result.steps().get(16);

        assertThat(duplicateCheckStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(duplicateCheckStep.message()).isEqualTo(
                "Duplicate tool request was skipped. type=GET_PORTFOLIO"
        );
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.EXECUTE_TOOL_REQUEST::equals)
                .hasSize(1);
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .filteredOn(HarnessStepType.CHECK_TOOL_CALL_LIMIT::equals)
                .hasSize(1);
        assertThat(result.toolResults())
                .extracting(
                        HarnessToolExecutionResult::status,
                        HarnessToolExecutionResult::reasonCode
                )
                .containsExactly(
                        tuple(
                                HarnessToolExecutionStatus.EXECUTED,
                                HarnessToolExecutionReasonCode.TOOL_EXECUTED
                        ),
                        tuple(
                                HarnessToolExecutionStatus.SKIPPED,
                                HarnessToolExecutionReasonCode.DUPLICATE_TOOL_REQUEST
                        )
                );
        assertThat(result.toolResults())
                .extracting(HarnessToolExecutionResult::request)
                .containsExactly(
                        HarnessToolRequest.portfolio(),
                        HarnessToolRequest.portfolio()
                );
    }

    private static class BuyingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            return new InvestmentDecision(
                    InvestmentAction.BUY,
                    "TEST",
                    10L,
                    100_000L,
                    "Test buy complete."
            );
        }
    }

    private static class OverLimitBuyingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            return new InvestmentDecision(
                    InvestmentAction.BUY,
                    "TEST",
                    20L,
                    100_000L,
                    "Test buy exceeds max order ratio."
            );
        }
    }

    private static class SellingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            return new InvestmentDecision(
                    InvestmentAction.SELL,
                    "TEST",
                    5L,
                    110_000L,
                    "Test sell complete."
            );
        }
    }

    private static class FailingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            throw new IllegalStateException("Test agent failure");
        }
    }

    private static class ToolRequestingInvestmentAgent extends InvestmentAgent {
        @Override
        public AgentNextAction next(HarnessRunContext context) {
            return AgentNextAction.requestTool(
                    new HarnessToolRequest(HarnessToolType.GET_PORTFOLIO)
            );
        }
    }

    private static class ToolResultUsingInvestmentAgent extends InvestmentAgent {
        @Override
        public AgentNextAction next(HarnessRunContext context) {
            if (context.toolResults().isEmpty()) {
                return AgentNextAction.requestTool(
                        new HarnessToolRequest(HarnessToolType.GET_PORTFOLIO)
                );
            }

            HarnessToolExecutionResult toolResult = context.toolResults().getFirst();

            if (toolResult.output().portfolioSnapshot() == null) {
                throw new IllegalStateException("Portfolio tool result is missing.");
            }

            return AgentNextAction.finalDecision(new InvestmentDecision(
                    InvestmentAction.HOLD,
                    null,
                    null,
                    null,
                    "Portfolio tool result received. cashAmountKrw="
                    + toolResult.output().portfolioSnapshot().cashAmountKrw()
            ));
        }
    }

    private static class MultipleToolResultUsingInvestmentAgent extends InvestmentAgent {
        @Override
        public AgentNextAction next(HarnessRunContext context) {
            if (context.toolResults().isEmpty()) {
                return AgentNextAction.requestTool(
                        new HarnessToolRequest(HarnessToolType.GET_PORTFOLIO)
                );
            }

            if (context.toolResults().size() == 1) {
                return AgentNextAction.requestTool(
                        new HarnessToolRequest(HarnessToolType.GET_MARKET)
                );
            }

            HarnessToolExecutionResult portfolioResult = context.toolResults().get(0);
            HarnessToolExecutionResult marketResult = context.toolResults().get(1);

            return AgentNextAction.finalDecision(new InvestmentDecision(
                    InvestmentAction.HOLD,
                    null,
                    null,
                    null,
                    "Multiple tool results received. cashAmountKrw="
                    + portfolioResult.output().portfolioSnapshot().cashAmountKrw()
                    + ", market="
                    + marketResult.output().marketSnapshot().market()
            ));
        }
    }

    private static class CurrentPriceUsingInvestmentAgent extends InvestmentAgent {
        @Override
        public AgentNextAction next(HarnessRunContext context) {
            if (context.toolResults().isEmpty()) {
                return AgentNextAction.requestTool(
                        HarnessToolRequest.currentPrice("005930")
                );
            }

            var currentPrice = context.toolResults()
                    .getFirst()
                    .output()
                    .currentPriceSnapshot();

            return AgentNextAction.finalDecision(new InvestmentDecision(
                    InvestmentAction.HOLD,
                    null,
                    null,
                    null,
                    "Current price received. symbol="
                    + currentPrice.symbol()
                    + ", priceKrw="
                    + currentPrice.priceKrw()
            ));
        }
    }
}
