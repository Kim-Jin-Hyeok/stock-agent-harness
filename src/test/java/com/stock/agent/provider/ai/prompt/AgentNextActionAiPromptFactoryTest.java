package com.stock.agent.provider.ai.prompt;

import com.stock.agent.provider.ai.request.AgentNextActionAiRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentNextActionAiPromptFactoryTest {
    private final AgentNextActionAiPromptFactory factory =
            new AgentNextActionAiPromptFactory();

    @Test
    void createsAgentNextActionPrompt() {
        AgentNextActionAiRequest request = request();

        AgentNextActionAiPrompt prompt = factory.create(request);

        assertThat(prompt.request()).isSameAs(request);
        assertThat(prompt.systemInstruction()).isNotBlank();
    }

    @Test
    void includesToolSelectionRules() {
        String instruction = normalizedInstruction();

        assertThat(instruction)
                .contains("Select only a tool listed in allowedToolTypes.")
                .contains(
                        "GET_CURRENT_PRICE and GET_DAILY_PRICE_HISTORY "
                                + "require a symbol from candidateSymbols."
                )
                .contains(
                        "GET_PORTFOLIO and GET_MARKET require a null symbol."
                )
                .contains(
                        "Do not repeat an equivalent request already "
                                + "present in toolResults."
                );
    }

    @Test
    void includesFinalDecisionRules() {
        String instruction = normalizedInstruction();

        assertThat(instruction)
                .contains(
                        "Return exactly one structured action: REQUEST_TOOL "
                                + "or FINAL_DECISION."
                )
                .contains(
                        "For FINAL_DECISION, include investmentDecision "
                                + "and set toolRequest to null."
                )
                .contains(
                        "BUY and SELL require a candidate symbol, a positive "
                                + "integer quantity"
                )
                .contains(
                        "HOLD requires symbol, quantity, and expectedPriceKrw "
                                + "to be null"
                )
                .contains(
                        "The harness and Risk Guard make the final "
                                + "authorization decision."
                );
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null.");
    }

    @Test
    void rejectsBlankSystemInstruction() {
        assertThatThrownBy(() -> new AgentNextActionAiPrompt(
                " ",
                request()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("systemInstruction must not be blank.");
    }

    private String normalizedInstruction() {
        return factory.create(request())
                .systemInstruction()
                .replaceAll("\\s+", " ");
    }

    private AgentNextActionAiRequest request() {
        return new AgentNextActionAiRequest(
                "DAY_TRADING_V1",
                1,
                InvestmentHorizon.DAY_TRADING,
                List.of(
                        HarnessToolType.GET_CURRENT_PRICE,
                        HarnessToolType.GET_DAILY_PRICE_HISTORY
                ),
                List.of("005930"),
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
                List.of()
        );
    }
}
