package com.stock.market.index.history.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketIndexDailyObservationRepository
        extends JpaRepository<MarketIndexDailyObservationEntity, Long> {
    List<MarketIndexDailyObservationEntity>
    findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
            String benchmarkId,
            LocalDate fromDate,
            LocalDate toDate
    );

    Optional<MarketIndexDailyObservationEntity>
    findTopByBenchmarkIdOrderByObservationDateDesc(String benchmarkId);

    Optional<MarketIndexDailyObservationEntity>
    findTopByBenchmarkIdOrderByObservationDateAsc(String benchmarkId);
}
