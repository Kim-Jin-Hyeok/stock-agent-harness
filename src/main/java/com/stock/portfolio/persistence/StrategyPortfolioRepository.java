package com.stock.portfolio.persistence;

import com.stock.strategy.profile.InvestmentHorizon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StrategyPortfolioRepository
        extends JpaRepository<StrategyPortfolioEntity, Long> {

    Optional<StrategyPortfolioEntity> findByStrategyIdAndStrategyVersionAndHorizon(
            String strategyId,
            int strategyVersion,
            InvestmentHorizon horizon
    );
}
