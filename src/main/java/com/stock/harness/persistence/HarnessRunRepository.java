package com.stock.harness.persistence;

import com.stock.strategy.profile.InvestmentHorizon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HarnessRunRepository extends JpaRepository<HarnessRunEntity, Long> {
    Optional<HarnessRunEntity> findByRunId(String runId);
    List<HarnessRunEntity> findAllByOrderByStartedAtDesc();
    Optional<HarnessRunEntity> findFirstByStrategyIdAndStrategyVersionAndHorizonOrderByStartedAtDesc(
            String strategyId,
            int strategyVersion,
            InvestmentHorizon horizon
    );
}
