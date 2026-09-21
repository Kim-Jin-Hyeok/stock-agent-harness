package com.stock.strategy.profile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InvestmentStrategyIdentityTest {

    @Test
    void createsStrategyIdentity() {
        InvestmentStrategyIdentity identity = new InvestmentStrategyIdentity(
                "SWING_MOMENTUM",
                2,
                InvestmentHorizon.SWING
        );

        assertThat(identity.strategyId()).isEqualTo("SWING_MOMENTUM");
        assertThat(identity.strategyVersion()).isEqualTo(2);
        assertThat(identity.horizon()).isEqualTo(InvestmentHorizon.SWING);
    }

    @Test
    void rejectsBlankStrategyId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvestmentStrategyIdentity(
                        " ",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ))
                .withMessage("strategyId must not be blank.");
    }

    @Test
    void rejectsNonPositiveStrategyVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InvestmentStrategyIdentity(
                        "LONG_TERM_VALUE",
                        0,
                        InvestmentHorizon.LONG_TERM
                ))
                .withMessage("strategyVersion must be at least 1.");
    }

    @Test
    void rejectsMissingHorizon() {
        assertThatNullPointerException()
                .isThrownBy(() -> new InvestmentStrategyIdentity(
                        "SWING_MOMENTUM",
                        1,
                        null
                ))
                .withMessage("horizon must not be null.");
    }
}
