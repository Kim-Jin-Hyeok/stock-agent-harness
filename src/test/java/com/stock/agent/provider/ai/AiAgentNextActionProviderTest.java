package com.stock.agent.provider.ai;

import com.stock.agent.AgentNextAction;
import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPrompt;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPromptFactory;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequestFactory;
import com.stock.harness.HarnessRunContext;
import com.stock.harness.HarnessRunLimits;
import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentNextActionProviderTest {
    private static final String SYMBOL = "005930";

    private final AgentNextActionAiRequestFactory requestFactory =
            new AgentNextActionAiRequestFactory();
    private final AgentNextActionAiPromptFactory promptFactory =
            new AgentNextActionAiPromptFactory();

    @Test
    void delegatesHarnessRunContextToAiExecutor() {
        AtomicReference<AgentNextActionAiPrompt> capturedPrompt =
                new AtomicReference<>();
        AgentNextAction expected = AgentNextAction.finalDecision(
                new InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        null,
                        null,
                        "AI selected HOLD."
                )
        );
        AiAgentNextActionProvider provider = provider(prompt -> {
            capturedPrompt.set(prompt);
            return expected;
        });

        AgentNextAction result = provider.next(context());

        assertThat(result).isSameAs(expected);
        assertThat(capturedPrompt.get()).isNotNull();
        assertThat(capturedPrompt.get().request().strategyId())
                .isEqualTo("DAY_TRADING_V1");
        assertThat(capturedPrompt.get().request().candidateSymbols())
                .containsExactly(SYMBOL);
    }

    @Test
    void propagatesAiExecutorFailure() {
        AiAgentNextActionProvider provider = provider(prompt -> {
            throw new IllegalStateException("OpenAI request failed.");
        });

        assertThatThrownBy(() -> provider.next(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI request failed.");
    }

    @Test
    void rejectsNullContext() {
        AiAgentNextActionProvider provider = provider(prompt ->
                AgentNextAction.finalDecision(new InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        null,
                        null,
                        "AI selected HOLD."
                ))
        );

        assertThatNullPointerException()
                .isThrownBy(() -> provider.next(null))
                .withMessage("context must not be null.");
    }

    @Test
    void rejectsNullAiExecutorResult() {
        AiAgentNextActionProvider provider = provider(prompt -> null);

        assertThatNullPointerException()
                .isThrownBy(() -> provider.next(context()))
                .withMessage("AI executor result must not be null.");
    }

    @Test
    void rejectsNullDependencies() {
        AgentNextActionAiExecutor executor = prompt ->
                AgentNextAction.finalDecision(new InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        null,
                        null,
                        "AI selected HOLD."
                ));

        assertThatNullPointerException()
                .isThrownBy(() -> new AiAgentNextActionProvider(
                        null,
                        promptFactory,
                        executor
                ))
                .withMessage("requestFactory must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new AiAgentNextActionProvider(
                        requestFactory,
                        null,
                        executor
                ))
                .withMessage("promptFactory must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new AiAgentNextActionProvider(
                        requestFactory,
                        promptFactory,
                        null
                ))
                .withMessage("executor must not be null.");
    }

    private AiAgentNextActionProvider provider(
            AgentNextActionAiExecutor executor
    ) {
        return new AiAgentNextActionProvider(
                requestFactory,
                promptFactory,
                executor
        );
    }

    private HarnessRunContext context() {
        return new HarnessRunContext(
                "run-1",
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ),
                new HarnessRunLimits(10, 5, 1, 5),
                HarnessAllowedTools.readOnly(),
                new PortfolioSnapshot(
                        10_000_000L,
                        10_000_000L,
                        List.of()
                ),
                new MarketSnapshot(
                        "KR",
                        true,
                        "Korean market is open."
                ),
                List.of(SYMBOL),
                List.of()
        );
    }
}
