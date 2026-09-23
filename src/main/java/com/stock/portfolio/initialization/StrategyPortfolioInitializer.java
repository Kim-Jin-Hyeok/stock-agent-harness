package com.stock.portfolio.initialization;

import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

public interface StrategyPortfolioInitializer {
    PortfolioSnapshot initialize(InvestmentStrategyIdentity strategyIdentity);
}
