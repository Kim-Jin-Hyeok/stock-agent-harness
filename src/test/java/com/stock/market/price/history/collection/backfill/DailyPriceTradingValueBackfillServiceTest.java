package com.stock.market.price.history.collection.backfill;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.collection.backfill.result.DailyPriceTradingValueBackfillStatus;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;
import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderException;
import com.stock.market.price.history.provider.error.DailyPriceHistoryProviderFailureType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyPriceTradingValueBackfillServiceTest {
    private static final String SYMBOL = "005930";
    private static final TradingVenueScope SCOPE = TradingVenueScope.INTEGRATED;
    private static final LocalDate FROM = LocalDate.of(2026, 9, 21);
    private static final LocalDate TO = FROM.plusDays(4);
    private static final DailyPriceHistoryRequest REQUEST = new DailyPriceHistoryRequest(SYMBOL, FROM, TO);

    private final DailyPriceHistoryProvider provider = mock(DailyPriceHistoryProvider.class);
    private final DailyPriceBarRepository repository = mock(DailyPriceBarRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final DailyPriceTradingValueBackfillService service = new DailyPriceTradingValueBackfillService(
            provider, repository, transactionManager, entityManager);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void skipsProviderAndWritesWhenNoStoredRowsExist() {
        var result = service.backfill(REQUEST, SCOPE);

        assertThat(result.status()).isEqualTo(DailyPriceTradingValueBackfillStatus.NO_TARGETS);
        assertThat(result.requestedRange()).isEqualTo(REQUEST);
        assertThat(result.expectedVenueScope()).isEqualTo(SCOPE);
        assertThat(result.collectionRange()).isNull();
        assertThat(result.targetCount()).isZero();
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        verifyNoInteractions(provider, entityManager);
        verify(repository, never()).flush();
        verify(repository, never()).findAllForTradingValueBackfill(anyString(), any(), any());
    }

    @Test
    void doesNotTreatKnownZeroAsMissing() {
        stored(List.of(entity(1, bar(FROM, 0L, SCOPE))));

        assertThat(service.backfill(REQUEST, SCOPE).status())
                .isEqualTo(DailyPriceTradingValueBackfillStatus.NO_TARGETS);
        verifyNoInteractions(provider);
        verify(repository, never()).flush();
    }

    @Test
    void fetchesOnlyMissingDateSpanAndFillsNullFieldsWithoutReplacingRows() {
        LocalDate first = FROM.plusDays(1);
        LocalDate last = FROM.plusDays(3);
        DailyPriceBarEntity missingBoth = entity(2, bar(first, null, null));
        DailyPriceBarEntity complete = entity(3, bar(first.plusDays(1), 500L, SCOPE));
        DailyPriceBarEntity missingScope = entity(4, bar(last, 0L, null));
        stored(List.of(entity(1, bar(FROM, 100L, SCOPE)), missingBoth,
                complete, missingScope, entity(5, bar(TO, 200L, SCOPE))));
        var range = new DailyPriceHistoryRequest(SYMBOL, first, last);
        when(provider.getDailyPriceHistory(range)).thenReturn(history(
                bar(first, 100L, SCOPE), bar(first.plusDays(1), 500L, SCOPE), bar(last, 0L, SCOPE)));
        when(repository.findAllForTradingValueBackfill(SYMBOL, first, last))
                .thenReturn(List.of(missingBoth, complete, missingScope));

        var result = service.backfill(REQUEST, SCOPE);

        assertThat(result.status()).isEqualTo(DailyPriceTradingValueBackfillStatus.BACKFILLED);
        assertThat(result.collectionRange()).isEqualTo(range);
        assertThat(result.targetCount()).isEqualTo(2);
        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(missingBoth.toBar()).isEqualTo(bar(first, 100L, SCOPE));
        assertThat(missingBoth.getId()).isEqualTo(2L);
        assertThat(missingScope.toBar()).isEqualTo(bar(last, 0L, SCOPE));
        assertThat(complete.toBar()).isEqualTo(bar(first.plusDays(1), 500L, SCOPE));
        verify(provider).getDailyPriceHistory(range);
        verify(entityManager).refresh(missingBoth, LockModeType.PESSIMISTIC_WRITE);
        verify(repository).flush();
    }

    @Test
    void rejectsKnownStoredVenueConflictWithoutProviderCall() {
        stored(List.of(entity(1, bar(FROM, null, TradingVenueScope.KRX))));

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Stored venue");
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"symbol", "before", "after", "empty", "missingDate", "value", "venue", "unknownVenue"})
    void rejectsIncompleteOrMismatchedResponsesBeforeAnyWrite(String scenario) {
        DailyPriceBarEntity first = entity(1, bar(FROM, null, null));
        DailyPriceBarEntity last = entity(2, bar(TO, null, null));
        stored(List.of(first, last));
        DailyPriceHistory response = switch (scenario) {
            case "symbol" -> new DailyPriceHistory("000660", List.of(bar(FROM, 100L, SCOPE), bar(TO, 200L, SCOPE)));
            case "before" -> history(bar(FROM.minusDays(1), 100L, SCOPE), bar(TO, 200L, SCOPE));
            case "after" -> history(bar(FROM, 100L, SCOPE), bar(TO.plusDays(1), 200L, SCOPE));
            case "empty" -> history();
            case "missingDate" -> history(bar(FROM, 100L, SCOPE));
            case "value" -> history(bar(FROM, 100L, SCOPE), bar(TO, null, SCOPE));
            case "venue" -> history(bar(FROM, 100L, SCOPE), bar(TO, 200L, TradingVenueScope.KRX));
            case "unknownVenue" -> history(bar(FROM, 100L, SCOPE), bar(TO, 200L, null));
            default -> throw new IllegalArgumentException(scenario);
        };
        when(provider.getDailyPriceHistory(REQUEST)).thenReturn(response);

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE)).isInstanceOf(IllegalStateException.class);

        assertThat(first.toBar()).isEqualTo(bar(FROM, null, null));
        assertThat(last.toBar()).isEqualTo(bar(TO, null, null));
        verify(repository, never()).findAllForTradingValueBackfill(anyString(), any(), any());
        verify(repository, never()).flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {"open", "high", "low", "close", "volume", "knownValue"})
    void rejectsOriginalOhlcvOrKnownValueConflicts(String field) {
        DailyPriceBar original = bar(FROM, field.equals("knownValue") ? 999L : null, null);
        stored(List.of(entity(1, original)));
        var range = new DailyPriceHistoryRequest(SYMBOL, FROM, FROM);
        DailyPriceBar changed = new DailyPriceBar(FROM,
                field.equals("open") ? 101 : 100,
                field.equals("high") ? 121 : 120,
                field.equals("low") ? 79 : 80,
                field.equals("close") ? 111 : 110,
                field.equals("volume") ? 1001 : 1000, 100L, SCOPE);
        when(provider.getDailyPriceHistory(range)).thenReturn(history(changed));

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).flush();
        verifyNoInteractions(entityManager);
    }

    @ParameterizedTest
    @EnumSource(DailyPriceHistoryProviderFailureType.class)
    void propagatesProviderFailureWithoutChangingStoredData(DailyPriceHistoryProviderFailureType failureType) {
        var row = entity(1, bar(FROM, null, null));
        stored(List.of(row));
        var range = new DailyPriceHistoryRequest(SYMBOL, FROM, FROM);
        var failure = new DailyPriceHistoryProviderException(failureType, "Provider failure.");
        when(provider.getDailyPriceHistory(range)).thenThrow(failure);

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE)).isSameAs(failure);
        assertThat(row.toBar()).isEqualTo(bar(FROM, null, null));
        verify(repository, never()).flush();
        verify(provider).getDailyPriceHistory(range);
    }

    @Test
    void rejectsNullProviderResponse() {
        stored(List.of(entity(1, bar(FROM, null, null))));

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("backfill history");
        verify(repository, never()).flush();
    }

    @Test
    void preservesCompatibleConcurrentFillAndReportsNoNewWrites() {
        stored(List.of(entity(1, bar(FROM, null, null))));
        var range = new DailyPriceHistoryRequest(SYMBOL, FROM, FROM);
        when(provider.getDailyPriceHistory(range)).thenReturn(history(bar(FROM, 100L, SCOPE)));
        var concurrent = entity(1, bar(FROM, 100L, SCOPE));
        when(repository.findAllForTradingValueBackfill(SYMBOL, FROM, FROM)).thenReturn(List.of(concurrent));

        var result = service.backfill(REQUEST, SCOPE);

        assertThat(result.status()).isEqualTo(DailyPriceTradingValueBackfillStatus.ALREADY_FILLED);
        assertThat(result.updatedCount()).isZero();
        assertThat(concurrent.toBar()).isEqualTo(bar(FROM, 100L, SCOPE));
        verify(repository, never()).flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "replaced", "ohlcv", "value", "venue", "removedValue", "removedVenue"})
    void rejectsConcurrentConflictsBeforeMutatingAnyRows(String scenario) {
        boolean knownValue = scenario.equals("removedValue");
        boolean knownVenue = scenario.equals("removedVenue");
        stored(List.of(entity(1, bar(FROM, knownValue ? 100L : null, knownVenue ? SCOPE : null)),
                entity(2, bar(TO, null, null))));
        when(provider.getDailyPriceHistory(REQUEST))
                .thenReturn(history(bar(FROM, 100L, SCOPE), bar(TO, 200L, SCOPE)));
        DailyPriceBar changed = scenario.equals("ohlcv")
                ? new DailyPriceBar(FROM, 100, 120, 80, 111, 1000, null, null)
                : bar(FROM, scenario.equals("value") ? 999L : null,
                        scenario.equals("venue") ? TradingVenueScope.NXT : null);
        var first = entity(scenario.equals("replaced") ? 99 : 1, changed);
        var last = entity(2, bar(TO, null, null));
        when(repository.findAllForTradingValueBackfill(SYMBOL, FROM, TO))
                .thenReturn(scenario.equals("deleted") ? List.of(last) : List.of(first, last));

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE)).isInstanceOf(IllegalStateException.class);
        assertThat(last.toBar()).isEqualTo(bar(TO, null, null));
        verify(repository, never()).flush();
    }

    @Test
    void rejectsCallerTransactionBeforeReadingOrFetching() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(repository, provider, transactionManager, entityManager);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> service.backfill(null, SCOPE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.backfill(REQUEST, null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository, provider);
    }

    private void stored(List<DailyPriceBarEntity> rows) {
        when(repository.findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(SYMBOL, FROM, TO))
                .thenReturn(rows);
    }

    private DailyPriceBarEntity entity(long id, DailyPriceBar bar) {
        var entity = DailyPriceBarEntity.from(SYMBOL, bar);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private DailyPriceHistory history(DailyPriceBar... bars) {
        return new DailyPriceHistory(SYMBOL, List.of(bars));
    }

    private DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope scope) {
        return new DailyPriceBar(date, 100, 120, 80, 110, 1000, value, scope);
    }
}
