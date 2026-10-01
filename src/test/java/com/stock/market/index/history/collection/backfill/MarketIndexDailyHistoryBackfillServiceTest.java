package com.stock.market.index.history.collection.backfill;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.collection.backfill.result.MarketIndexDailyHistoryBackfillStatus;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationEntity;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import com.stock.market.index.history.provider.MarketIndexDailyHistoryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketIndexDailyHistoryBackfillServiceTest {
    private static final LocalDate FROM = LocalDate.of(2023, 9, 25);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final LocalDate EARLIEST = LocalDate.of(2025, 9, 30);
    private static final MarketIndexDailyHistoryRequest REQUEST = request(FROM, TO);

    private final MarketIndexDailyHistoryProvider provider = mock(MarketIndexDailyHistoryProvider.class);
    private final MarketIndexDailyObservationRepository repository = mock(MarketIndexDailyObservationRepository.class);
    private final MarketIndexDailyHistoryBackfillService service =
            new MarketIndexDailyHistoryBackfillService(provider, repository);

    @Test
    void fetchesWholeRequestedRangeWhenNoHistoryExists() {
        when(repository.findTopByBenchmarkIdOrderByObservationDateAsc("KOSPI"))
                .thenReturn(Optional.empty());
        when(provider.getDailyHistory(REQUEST)).thenReturn(history(FROM, TO));

        var result = service.backfill(REQUEST);

        assertThat(result.status()).isEqualTo(MarketIndexDailyHistoryBackfillStatus.BACKFILLED);
        assertThat(result.requestedRange()).isEqualTo(REQUEST);
        assertThat(result.collectionRange()).isEqualTo(REQUEST);
        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.fetchedFromDate()).isEqualTo(FROM);
        assertThat(result.fetchedToDate()).isEqualTo(TO);
        verify(repository).saveAllAndFlush(argThat(entities -> {
            var dates = new ArrayList<LocalDate>();
            entities.forEach(entity -> dates.add(entity.getObservationDate()));
            return dates.equals(List.of(FROM, TO));
        }));
    }

    @Test
    void fetchesOnlyDatesBeforeEarliestStoredObservation() {
        var actualRequest = request(FROM, EARLIEST.minusDays(1));
        when(repository.findTopByBenchmarkIdOrderByObservationDateAsc("KOSPI"))
                .thenReturn(Optional.of(entity(EARLIEST)));
        when(provider.getDailyHistory(actualRequest))
                .thenReturn(history(FROM, EARLIEST.minusDays(1)));

        var result = service.backfill(REQUEST);

        assertThat(result.collectionRange()).isEqualTo(actualRequest);
        verify(provider).getDailyHistory(actualRequest);
        verify(provider, never()).getDailyHistory(REQUEST);
    }

    @Test
    void keepsRequestedEndWhenItIsBeforeStoredHistory() {
        var earlierRequest = request(FROM, EARLIEST.minusDays(10));
        when(repository.findTopByBenchmarkIdOrderByObservationDateAsc("KOSPI"))
                .thenReturn(Optional.of(entity(EARLIEST)));
        when(provider.getDailyHistory(earlierRequest))
                .thenReturn(history(FROM, earlierRequest.toDate()));

        assertThat(service.backfill(earlierRequest).collectionRange())
                .isEqualTo(earlierRequest);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-09-25", "2023-09-22"})
    void skipsProviderWhenStoredHistoryAlreadyStartsAtOrBeforeRequestedStart(String storedDate) {
        when(repository.findTopByBenchmarkIdOrderByObservationDateAsc("KOSPI"))
                .thenReturn(Optional.of(entity(LocalDate.parse(storedDate))));

        var result = service.backfill(REQUEST);

        assertThat(result.status()).isEqualTo(MarketIndexDailyHistoryBackfillStatus.NO_EARLIER_RANGE);
        assertThat(result.collectionRange()).isNull();
        assertThat(result.savedCount()).isZero();
        verifyNoInteractions(provider);
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void reportsEmptyResponseWithoutSavingOrClaimingBackfilled() {
        when(provider.getDailyHistory(REQUEST))
                .thenReturn(new MarketIndexDailyHistory("KOSPI", List.of()));

        var result = service.backfill(REQUEST);

        assertThat(result.status()).isEqualTo(MarketIndexDailyHistoryBackfillStatus.NO_DATA);
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.fetchedFromDate()).isNull();
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void reportsActualReturnedDatesWhenProviderReturnsOnlyPartOfRange() {
        when(provider.getDailyHistory(REQUEST))
                .thenReturn(history(FROM.plusDays(20), TO.minusDays(20)));

        var result = service.backfill(REQUEST);

        assertThat(result.collectionRange()).isEqualTo(REQUEST);
        assertThat(result.fetchedFromDate()).isEqualTo(FROM.plusDays(20));
        assertThat(result.fetchedToDate()).isEqualTo(TO.minusDays(20));
        assertThat(result.savedCount()).isEqualTo(2);
    }

    @Test
    void preservesObservationsStoredWhileProviderWasRunning() {
        when(provider.getDailyHistory(REQUEST)).thenReturn(history(FROM, TO));
        when(repository.findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                "KOSPI", FROM, TO
        )).thenReturn(List.of(entity(FROM)));

        var result = service.backfill(REQUEST);

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
        verify(repository).saveAllAndFlush(argThat(entities -> {
            var saved = new ArrayList<MarketIndexDailyObservationEntity>();
            entities.forEach(saved::add);
            return saved.size() == 1 && saved.getFirst().getObservationDate().equals(TO);
        }));
    }

    @Test
    void skipsSaveWhenAllReturnedDatesAlreadyExist() {
        when(provider.getDailyHistory(REQUEST)).thenReturn(history(FROM, TO));
        when(repository.findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                "KOSPI", FROM, TO
        )).thenReturn(List.of(entity(FROM), entity(TO)));

        assertThat(service.backfill(REQUEST).savedCount()).isZero();
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsDifferentBenchmarkBeforeSaving() {
        when(provider.getDailyHistory(REQUEST)).thenReturn(new MarketIndexDailyHistory(
                "KOSDAQ", List.of(observation(FROM))
        ));

        assertThatThrownBy(() -> service.backfill(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("collected history benchmarkId must match backfill request.");
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-09-24", "2026-10-01"})
    void rejectsOutOfRangeObservationBeforeSaving(String date) {
        when(provider.getDailyHistory(REQUEST))
                .thenReturn(history(LocalDate.parse(date)));

        assertThatThrownBy(() -> service.backfill(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("collected observation must be within backfill range.");
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsOverlapWithOriginalStoredRange() {
        var actualRequest = request(FROM, EARLIEST.minusDays(1));
        when(repository.findTopByBenchmarkIdOrderByObservationDateAsc("KOSPI"))
                .thenReturn(Optional.of(entity(EARLIEST)));
        when(provider.getDailyHistory(actualRequest)).thenReturn(history(FROM, EARLIEST));

        assertThatThrownBy(() -> service.backfill(REQUEST)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void propagatesProviderFailureWithoutSaving() {
        when(provider.getDailyHistory(REQUEST)).thenThrow(new IllegalStateException("Page limit exceeded."));

        assertThatThrownBy(() -> service.backfill(REQUEST))
                .hasMessage("Page limit exceeded.");
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void propagatesPersistenceFailureInsteadOfReturningSuccess() {
        when(provider.getDailyHistory(REQUEST)).thenReturn(history(FROM));
        when(repository.saveAllAndFlush(anyList())).thenThrow(new IllegalStateException("Insert failed."));

        assertThatThrownBy(() -> service.backfill(REQUEST)).hasMessage("Insert failed.");
    }

    private static MarketIndexDailyHistoryRequest request(LocalDate from, LocalDate to) {
        return new MarketIndexDailyHistoryRequest("KOSPI", from, to);
    }

    private static MarketIndexDailyHistory history(LocalDate... dates) {
        return new MarketIndexDailyHistory("KOSPI", Arrays.stream(dates)
                .map(MarketIndexDailyHistoryBackfillServiceTest::observation).toList());
    }

    private static MarketIndexDailyObservation observation(LocalDate date) {
        return new MarketIndexDailyObservation(date, new BigDecimal("3421.371234"));
    }

    private static MarketIndexDailyObservationEntity entity(LocalDate date) {
        return MarketIndexDailyObservationEntity.from("KOSPI", observation(date));
    }
}
