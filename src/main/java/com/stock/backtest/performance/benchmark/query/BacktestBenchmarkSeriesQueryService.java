package com.stock.backtest.performance.benchmark.query;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkObservation;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkSeries;
import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.query.MarketIndexDailyHistoryQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class BacktestBenchmarkSeriesQueryService {
    private final MarketIndexDailyHistoryQueryService historyQueryService;

    public BacktestBenchmarkSeriesQueryService(
            MarketIndexDailyHistoryQueryService historyQueryService
    ) {
        this.historyQueryService = Objects.requireNonNull(
                historyQueryService,
                "historyQueryService must not be null."
        );
    }

    public BacktestBenchmarkSeries getSeries(
            String benchmarkId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        MarketIndexDailyHistoryRequest request =
                new MarketIndexDailyHistoryRequest(
                        benchmarkId,
                        fromDate,
                        toDate
                );
        MarketIndexDailyHistory history = Objects.requireNonNull(
                historyQueryService.getDailyHistory(request),
                "benchmark history must not be null."
        );
        if (!benchmarkId.equals(history.benchmarkId())) {
            throw new IllegalStateException(
                    "Benchmark history benchmarkId must match request "
                            + "benchmarkId."
            );
        }
        if (history.observations().isEmpty()) {
            throw new IllegalStateException(
                    "Stored benchmark history must not be empty. "
                            + "benchmarkId=" + benchmarkId
                            + ", fromDate=" + fromDate
                            + ", toDate=" + toDate
            );
        }

        List<BacktestBenchmarkObservation> observations = history
                .observations()
                .stream()
                .map(observation -> new BacktestBenchmarkObservation(
                        observation.observationDate(),
                        observation.closeValue()
                ))
                .toList();
        return new BacktestBenchmarkSeries(benchmarkId, observations);
    }
}
