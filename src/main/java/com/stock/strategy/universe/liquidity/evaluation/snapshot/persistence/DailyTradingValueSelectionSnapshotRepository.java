package com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTradingValueSelectionSnapshotRepository
        extends JpaRepository<DailyTradingValueSelectionSnapshotEntity, Long> {
}
