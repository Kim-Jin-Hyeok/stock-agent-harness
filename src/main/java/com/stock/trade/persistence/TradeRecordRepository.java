package com.stock.trade.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRecordRepository extends JpaRepository<TradeRecordEntity, Long> {
    List<TradeRecordEntity> findAllByRunIdOrderByExecutedAtDesc(String runId);
    List<TradeRecordEntity> findAllByOrderByExecutedAtDesc();
}
