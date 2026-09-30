package com.stock.backtest.performance.benchmark.query;

import com.stock.backtest.performance.benchmark.BacktestBenchmarkSeries;
import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.query.MarketIndexDailyHistoryQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BacktestBenchmarkSeriesQueryServiceTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 29);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);
    private static final MarketIndexDailyHistoryRequest HISTORY_REQUEST =
            new MarketIndexDailyHistoryRequest(
                    BENCHMARK_ID,
                    FROM_DATE,
                    TO_DATE
            );

    @Test
    void returnsBenchmarkSeriesFromStoredMarketIndexHistory() {
        MarketIndexDailyHistoryQueryService historyQueryService = mock(
                MarketIndexDailyHistoryQueryService.class
        );
        BacktestBenchmarkSeriesQueryService service =
                new BacktestBenchmarkSeriesQueryService(
                        historyQueryService
                );
        MarketIndexDailyObservation first = observation(
                FROM_DATE,
                "3420.250000"
        );
        MarketIndexDailyObservation second = observation(
                TO_DATE,
                "3421.371234"
        );
        when(historyQueryService.getDailyHistory(HISTORY_REQUEST))
                .thenReturn(new MarketIndexDailyHistory(
                        BENCHMARK_ID,
                        List.of(first, second)
                ));

        BacktestBenchmarkSeries series = service.getSeries(
                BENCHMARK_ID,
                FROM_DATE,
                TO_DATE
        );

        assertThat(series.benchmarkId()).isEqualTo(BENCHMARK_ID);
        assertThat(series.observations())
                .extracting(
                        observation -> observation.observationDate(),
                        observation -> observation.closeValue()
                )
                .containsExactly(
                        tuple(FROM_DATE, new BigDecimal("3420.250000")),
                        tuple(TO_DATE, new BigDecimal("3421.371234"))
                );
        verify(historyQueryService).getDailyHistory(HISTORY_REQUEST);
    }

    @Test
    void rejectsEmptyStoredBenchmarkHistory() {
        MarketIndexDailyHistoryQueryService historyQueryService = mock(
                MarketIndexDailyHistoryQueryService.class
        );
        BacktestBenchmarkSeriesQueryService service =
                new BacktestBenchmarkSeriesQueryService(
                        historyQueryService
                );
        when(historyQueryService.getDailyHistory(HISTORY_REQUEST))
                .thenReturn(new MarketIndexDailyHistory(
                        BENCHMARK_ID,
                        List.of()
                ));

        assertThatThrownBy(() -> service.getSeries(
                BENCHMARK_ID,
                FROM_DATE,
                TO_DATE
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Stored benchmark history must not be empty. "
                                + "benchmarkId=KOSPI, "
                                + "fromDate=2026-09-29, "
                                + "toDate=2026-09-30"
                );
    }

    @Test
    void rejectsHistoryForDifferentBenchmark() {
        MarketIndexDailyHistoryQueryService historyQueryService = mock(
                MarketIndexDailyHistoryQueryService.class
        );
        BacktestBenchmarkSeriesQueryService service =
                new BacktestBenchmarkSeriesQueryService(
                        historyQueryService
                );
        when(historyQueryService.getDailyHistory(HISTORY_REQUEST))
                .thenReturn(new MarketIndexDailyHistory(
                        "KOSDAQ",
                        List.of(observation(FROM_DATE, "1000.00"))
                ));

        assertThatThrownBy(() -> service.getSeries(
                BENCHMARK_ID,
                FROM_DATE,
                TO_DATE
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Benchmark history benchmarkId must match request "
                                + "benchmarkId."
                );
    }

    @Test
    void rejectsInvalidRequestBeforeHistoryQuery() {
        MarketIndexDailyHistoryQueryService historyQueryService = mock(
                MarketIndexDailyHistoryQueryService.class
        );
        BacktestBenchmarkSeriesQueryService service =
                new BacktestBenchmarkSeriesQueryService(
                        historyQueryService
                );

        assertThatThrownBy(() -> service.getSeries(
                " ",
                FROM_DATE,
                TO_DATE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benchmarkId must not be blank.");
        verifyNoInteractions(historyQueryService);
    }

    private MarketIndexDailyObservation observation(
            LocalDate observationDate,
            String closeValue
    ) {
        return new MarketIndexDailyObservation(
                observationDate,
                new BigDecimal(closeValue)
        );
    }
}
