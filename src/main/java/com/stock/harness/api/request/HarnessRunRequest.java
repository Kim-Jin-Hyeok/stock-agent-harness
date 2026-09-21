package com.stock.harness.api.request;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HarnessRunRequest(
        @NotBlank String strategyId,
        @Min(1) int strategyVersion,
        @NotNull InvestmentHorizon horizon
) {
    public InvestmentStrategyIdentity toStrategyIdentity() {
        return new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
    }
}
