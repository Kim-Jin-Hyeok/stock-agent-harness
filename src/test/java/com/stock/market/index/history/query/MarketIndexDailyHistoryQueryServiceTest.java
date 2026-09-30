package com.stock.market.index.history.query;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationEntity;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketIndexDailyHistoryQueryServiceTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 29);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);
    private static final MarketIndexDailyHistoryRequest REQUEST =
            new MarketIndexDailyHistoryRequest(
                    BENCHMARK_ID,
                    FROM_DATE,
                    TO_DATE
            );

    @Test
    void returnsDailyHistoryFromStoredObservationsInDateOrder() {
        MarketIndexDailyObservationRepository repository = mock(
                MarketIndexDailyObservationRepository.class
        );
        MarketIndexDailyHistoryQueryService service =
                new MarketIndexDailyHistoryQueryService(repository);
        MarketIndexDailyObservation first = observation(
                FROM_DATE,
                "3420.25"
        );
        MarketIndexDailyObservation second = observation(
                TO_DATE,
                "3421.37"
        );
        when(repository
                .findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                        BENCHMARK_ID,
                        FROM_DATE,
                        TO_DATE
                )).thenReturn(List.of(
                        entity(second),
                        entity(first)
                ));

        MarketIndexDailyHistory history = service.getDailyHistory(REQUEST);

        assertThat(history.benchmarkId()).isEqualTo(BENCHMARK_ID);
        assertThat(history.observations()).containsExactly(first, second);
        verify(repository)
                .findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                        BENCHMARK_ID,
                        FROM_DATE,
                        TO_DATE
                );
    }

    @Test
    void returnsEmptyHistoryWhenStoredObservationsDoNotExist() {
        MarketIndexDailyObservationRepository repository = mock(
                MarketIndexDailyObservationRepository.class
        );
        MarketIndexDailyHistoryQueryService service =
                new MarketIndexDailyHistoryQueryService(repository);
        when(repository
                .findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                        BENCHMARK_ID,
                        FROM_DATE,
                        TO_DATE
                )).thenReturn(List.of());

        MarketIndexDailyHistory history = service.getDailyHistory(REQUEST);

        assertThat(history.benchmarkId()).isEqualTo(BENCHMARK_ID);
        assertThat(history.observations()).isEmpty();
    }

    @Test
    void rejectsNullRequestBeforeRepositoryCall() {
        MarketIndexDailyObservationRepository repository = mock(
                MarketIndexDailyObservationRepository.class
        );
        MarketIndexDailyHistoryQueryService service =
                new MarketIndexDailyHistoryQueryService(repository);

        assertThatThrownBy(() -> service.getDailyHistory(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null.");
        verifyNoInteractions(repository);
    }

    private MarketIndexDailyObservationEntity entity(
            MarketIndexDailyObservation observation
    ) {
        return MarketIndexDailyObservationEntity.from(
                BENCHMARK_ID,
                observation
        );
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
