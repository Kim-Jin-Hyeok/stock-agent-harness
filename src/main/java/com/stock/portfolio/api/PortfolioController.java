package com.stock.portfolio.api;

import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private final PortfolioService portfolioService;

    @GetMapping
    public PortfolioSnapshot getPortfolio(
            @RequestParam @NotBlank String strategyId,
            @RequestParam @Min(1) int strategyVersion,
            @RequestParam @NotNull InvestmentHorizon horizon
    ) {
        return portfolioService.getCurrentSnapshot(
                strategyIdentity(strategyId, strategyVersion, horizon)
        );
    }

    @PostMapping("/reset")
    public void reset(
            @RequestParam @NotBlank String strategyId,
            @RequestParam @Min(1) int strategyVersion,
            @RequestParam @NotNull InvestmentHorizon horizon
    ) {
        portfolioService.reset(strategyIdentity(strategyId, strategyVersion, horizon));
    }

    private InvestmentStrategyIdentity strategyIdentity(
            String strategyId,
            int strategyVersion,
            InvestmentHorizon horizon
    ) {
        return new InvestmentStrategyIdentity(strategyId, strategyVersion, horizon);
    }
}
