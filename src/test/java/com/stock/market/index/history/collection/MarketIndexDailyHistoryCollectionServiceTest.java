package com.stock.market.index.history.collection;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationEntity;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import com.stock.market.index.history.provider.MarketIndexDailyHistoryProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketIndexDailyHistoryCollectionServiceTest {
    private static final String BENCHMARK_ID = "KOSPI";
    private static final LocalDate FROM_DATE = LocalDate.of(2023, 9, 30);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);
    private static final MarketIndexDailyHistoryRequest REQUEST =
            new MarketIndexDailyHistoryRequest(
                    BENCHMARK_ID,
                    FROM_DATE,
                    TO_DATE
            );

    @Test
    void collectsRequestedRangeWhenHistoryDoesNotExist() {
        Dependencies dependencies = dependencies();
        List<MarketIndexDailyObservation> observations = List.of(
                observation(FROM_DATE),
                observation(TO_DATE)
        );
        when(dependencies.repository()
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        BENCHMARK_ID
                )).thenReturn(Optional.empty());
        when(dependencies.provider().getDailyHistory(REQUEST))
                .thenReturn(new MarketIndexDailyHistory(
                        BENCHMARK_ID,
                        observations
                ));

        MarketIndexDailyHistoryCollectionResult result =
                dependencies.service().collect(REQUEST);

        assertThat(result.status()).isEqualTo(
                MarketIndexDailyHistoryCollectionStatus.COLLECTED
        );
        assertThat(result.actualFromDate()).isEqualTo(FROM_DATE);
        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
        verify(dependencies.provider()).getDailyHistory(REQUEST);
        verify(dependencies.repository()).saveAll(anyList());
    }

    @Test
    void collectsOnlyDatesAfterLatestStoredObservation() {
        Dependencies dependencies = dependencies();
        LocalDate latestDate = LocalDate.of(2026, 9, 28);
        MarketIndexDailyHistoryRequest incrementalRequest =
                new MarketIndexDailyHistoryRequest(
                        BENCHMARK_ID,
                        latestDate.plusDays(1),
                        TO_DATE
                );
        when(dependencies.repository()
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        BENCHMARK_ID
                )).thenReturn(Optional.of(entity(latestDate)));
        when(dependencies.provider().getDailyHistory(incrementalRequest))
                .thenReturn(new MarketIndexDailyHistory(
                        BENCHMARK_ID,
                        List.of(
                                observation(latestDate.plusDays(1)),
                                observation(TO_DATE)
                        )
                ));

        MarketIndexDailyHistoryCollectionResult result =
                dependencies.service().collect(REQUEST);

        assertThat(result.status()).isEqualTo(
                MarketIndexDailyHistoryCollectionStatus.COLLECTED
        );
        assertThat(result.actualFromDate())
                .isEqualTo(latestDate.plusDays(1));
        assertThat(result.fetchedCount()).isEqualTo(2);
        verify(dependencies.provider()).getDailyHistory(incrementalRequest);
    }

    @Test
    void skipsProviderCallWhenHistoryIsAlreadyUpToDate() {
        Dependencies dependencies = dependencies();
        when(dependencies.repository()
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        BENCHMARK_ID
                )).thenReturn(Optional.of(entity(TO_DATE)));

        MarketIndexDailyHistoryCollectionResult result =
                dependencies.service().collect(REQUEST);

        assertThat(result.status()).isEqualTo(
                MarketIndexDailyHistoryCollectionStatus.ALREADY_UP_TO_DATE
        );
        assertThat(result.actualFromDate()).isNull();
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.savedCount()).isZero();
        verifyNoInteractions(dependencies.provider());
        verify(dependencies.repository(), never()).saveAll(anyList());
    }

    @Test
    void doesNotSaveWhenProviderReturnsNoObservations() {
        Dependencies dependencies = dependencies();
        when(dependencies.repository()
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        BENCHMARK_ID
                )).thenReturn(Optional.empty());
        when(dependencies.provider().getDailyHistory(REQUEST))
                .thenReturn(new MarketIndexDailyHistory(
                        BENCHMARK_ID,
                        List.of()
                ));

        MarketIndexDailyHistoryCollectionResult result =
                dependencies.service().collect(REQUEST);

        assertThat(result.status()).isEqualTo(
                MarketIndexDailyHistoryCollectionStatus.COLLECTED
        );
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.savedCount()).isZero();
        verify(dependencies.repository(), never()).saveAll(anyList());
    }

    @Test
    void rejectsHistoryForDifferentBenchmark() {
        Dependencies dependencies = dependencies();
        when(dependencies.repository()
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        BENCHMARK_ID
                )).thenReturn(Optional.empty());
        when(dependencies.provider().getDailyHistory(REQUEST))
                .thenReturn(new MarketIndexDailyHistory(
                        "KOSDAQ",
                        List.of(observation(TO_DATE))
                ));

        assertThatThrownBy(() -> dependencies.service().collect(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "collected history benchmarkId must match request "
                                + "benchmarkId."
                );
        verify(dependencies.repository(), never()).saveAll(anyList());
    }

    @Test
    void rejectsObservationOutsideRequestedRange() {
        Dependencies dependencies = dependencies();
        when(dependencies.repository()
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        BENCHMARK_ID
                )).thenReturn(Optional.empty());
        when(dependencies.provider().getDailyHistory(REQUEST))
                .thenReturn(new MarketIndexDailyHistory(
                        BENCHMARK_ID,
                        List.of(observation(FROM_DATE.minusDays(1)))
                ));

        assertThatThrownBy(() -> dependencies.service().collect(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "collected observation must be within requested "
                                + "range."
                );
        verify(dependencies.repository(), never()).saveAll(anyList());
    }

    private Dependencies dependencies() {
        MarketIndexDailyHistoryProvider provider = mock(
                MarketIndexDailyHistoryProvider.class
        );
        MarketIndexDailyObservationRepository repository = mock(
                MarketIndexDailyObservationRepository.class
        );
        return new Dependencies(
                provider,
                repository,
                new MarketIndexDailyHistoryCollectionService(
                        provider,
                        repository
                )
        );
    }

    private MarketIndexDailyObservationEntity entity(
            LocalDate observationDate
    ) {
        return MarketIndexDailyObservationEntity.from(
                BENCHMARK_ID,
                observation(observationDate)
        );
    }

    private MarketIndexDailyObservation observation(
            LocalDate observationDate
    ) {
        return new MarketIndexDailyObservation(
                observationDate,
                new BigDecimal("3421.37")
        );
    }

    private record Dependencies(
            MarketIndexDailyHistoryProvider provider,
            MarketIndexDailyObservationRepository repository,
            MarketIndexDailyHistoryCollectionService service
    ) {
    }
}
