package com.stock.agent.provider.rulebased;

import com.stock.agent.AgentNextAction;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.harness.HarnessRunContext;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.Objects;

public class StrategyRuleBasedAgentNextActionProvider
        implements AgentNextActionProvider {
    private static final String SWING_V1_STRATEGY_ID = "SWING_V1";
    private static final int SWING_V1_STRATEGY_VERSION = 1;

    private final AgentNextActionProvider defaultProvider;
    private final AgentNextActionProvider swingV1Provider;

    public StrategyRuleBasedAgentNextActionProvider(
            AgentNextActionProvider defaultProvider,
            AgentNextActionProvider swingV1Provider
    ) {
        this.defaultProvider = Objects.requireNonNull(
                defaultProvider,
                "defaultProvider must not be null."
        );
        this.swingV1Provider = Objects.requireNonNull(
                swingV1Provider,
                "swingV1Provider must not be null."
        );
    }

    @Override
    public AgentNextAction next(HarnessRunContext context) {
        Objects.requireNonNull(context, "context must not be null.");
        InvestmentStrategyIdentity strategyIdentity =
                context.strategyIdentity();

        if (isSwingV1(strategyIdentity)) {
            return swingV1Provider.next(context);
        }
        if (isUnsupportedSwingStrategy(strategyIdentity)) {
            throw new IllegalStateException(
                    "Unsupported rule-based swing strategy. strategyId="
                            + strategyIdentity.strategyId()
                            + ", strategyVersion="
                            + strategyIdentity.strategyVersion()
                            + ", horizon="
                            + strategyIdentity.horizon()
            );
        }
        return defaultProvider.next(context);
    }

    private boolean isSwingV1(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        return SWING_V1_STRATEGY_ID.equals(strategyIdentity.strategyId())
                && strategyIdentity.strategyVersion()
                == SWING_V1_STRATEGY_VERSION
                && strategyIdentity.horizon() == InvestmentHorizon.SWING;
    }

    private boolean isUnsupportedSwingStrategy(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        return strategyIdentity.horizon() == InvestmentHorizon.SWING
                || SWING_V1_STRATEGY_ID.equals(
                        strategyIdentity.strategyId()
                );
    }
}
