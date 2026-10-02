package com.stock.market.price.history.collection.backfill;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.collection.backfill.result.DailyPriceTradingValueBackfillStatus;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DailyPriceTradingValueBackfillIntegrationTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 21);
    private static final LocalDate TO = FROM.plusDays(2);
    private static final TradingVenueScope SCOPE = TradingVenueScope.INTEGRATED;
    private static final DailyPriceHistoryRequest REQUEST = new DailyPriceHistoryRequest(SYMBOL, FROM, TO);

    @Autowired
    private DailyPriceBarRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    private DailyPriceHistoryProvider provider;
    private DailyPriceTradingValueBackfillService service;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
        provider = mock(DailyPriceHistoryProvider.class);
        service = new DailyPriceTradingValueBackfillService(provider, repository, transactionManager, entityManager);
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAllInBatch();
    }

    @Test
    void persistsBackfillWithRequestScopedPersistenceContext() {
        var row = repository.saveAndFlush(entity(SYMBOL, bar(FROM, null, null)));
        var range = new DailyPriceHistoryRequest(SYMBOL, FROM, FROM);
        when(provider.getDailyPriceHistory(range)).thenReturn(history(bar(FROM, 100L, SCOPE)));
        EntityManager requestContext = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(requestContext));
        try {
            assertThat(service.backfill(REQUEST, SCOPE).updatedCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT trading_value_krw FROM daily_price_bar WHERE id = ?",
                    Long.class, row.getId())).isEqualTo(100L);
            assertThat(jdbc.queryForObject("SELECT trading_venue_scope FROM daily_price_bar WHERE id = ?",
                    String.class, row.getId())).isEqualTo(SCOPE.name());
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            requestContext.close();
        }
    }

    @Test
    void commitsOnlyMissingMetadataAndReplayMakesNoFurtherProviderCall() {
        var original = repository.saveAllAndFlush(List.of(
                entity(SYMBOL, bar(FROM, null, null)),
                entity(SYMBOL, bar(FROM.plusDays(1), 0L, null)),
                entity(SYMBOL, bar(TO, null, SCOPE)),
                entity(SYMBOL, bar(TO.plusDays(1), 900L, TradingVenueScope.KRX)),
                entity("000660", bar(FROM, null, null))));
        var ids = original.stream().map(DailyPriceBarEntity::getId).toList();
        when(provider.getDailyPriceHistory(REQUEST)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return history(bar(FROM, 100L, SCOPE), bar(FROM.plusDays(1), 0L, SCOPE), bar(TO, 300L, SCOPE));
        });

        var first = service.backfill(REQUEST, SCOPE);
        var replay = service.backfill(REQUEST, SCOPE);

        assertThat(first.status()).isEqualTo(DailyPriceTradingValueBackfillStatus.BACKFILLED);
        assertThat(first.updatedCount()).isEqualTo(3);
        assertThat(replay.status()).isEqualTo(DailyPriceTradingValueBackfillStatus.NO_TARGETS);
        assertThat(repository.count()).isEqualTo(5);
        assertThat(repository.findAllById(ids)).extracting(DailyPriceBarEntity::getId).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(storedBars()).containsExactly(
                bar(FROM, 100L, SCOPE), bar(FROM.plusDays(1), 0L, SCOPE), bar(TO, 300L, SCOPE));
        assertThat(repository.findById(ids.get(3)).orElseThrow().toBar())
                .isEqualTo(bar(TO.plusDays(1), 900L, TradingVenueScope.KRX));
        assertThat(repository.findById(ids.get(4)).orElseThrow().toBar()).isEqualTo(bar(FROM, null, null));
        verify(provider).getDailyPriceHistory(REQUEST);
    }

    @Test
    void doesNotInsertFetchedDatesMissingFromDatabase() {
        repository.saveAllAndFlush(List.of(entity(SYMBOL, bar(FROM, null, null)), entity(SYMBOL, bar(TO, null, null))));
        when(provider.getDailyPriceHistory(REQUEST)).thenReturn(history(
                bar(FROM, 100L, SCOPE), bar(FROM.plusDays(1), 200L, SCOPE), bar(TO, 300L, SCOPE)));

        var result = service.backfill(REQUEST, SCOPE);

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(storedBars()).containsExactly(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE));
    }

    @Test
    void leavesAllRowsUnchangedWhenLastFetchedBarConflicts() {
        var original = List.of(bar(FROM, null, null), bar(TO, null, null));
        repository.saveAllAndFlush(original.stream().map(bar -> entity(SYMBOL, bar)).toList());
        when(provider.getDailyPriceHistory(REQUEST)).thenReturn(history(bar(FROM, 100L, SCOPE),
                new DailyPriceBar(TO, 100, 120, 80, 111, 1000, 300L, SCOPE)));

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE)).isInstanceOf(IllegalStateException.class);

        assertThat(storedBars()).containsExactlyElementsOf(original);
    }

    @Test
    void rollsBackWholeBatchOnDatabaseConstraintFailureDuringFlush() {
        repository.saveAllAndFlush(List.of(entity(SYMBOL, bar(FROM, null, null)), entity(SYMBOL, bar(TO, null, null))));
        jdbc.execute("ALTER TABLE daily_price_bar ADD CONSTRAINT backfill_test_value_check "
                + "CHECK (trading_value_krw IS NULL OR trading_value_krw <> 300)");
        try {
            when(provider.getDailyPriceHistory(REQUEST)).thenReturn(history(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE)));

            assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE)).isInstanceOf(DataIntegrityViolationException.class);

            assertThat(storedBars()).containsExactly(bar(FROM, null, null), bar(TO, null, null));
            assertThat(repository.count()).isEqualTo(2);
        } finally {
            jdbc.execute("ALTER TABLE daily_price_bar DROP CONSTRAINT backfill_test_value_check");
        }
    }

    @Test
    void preservesDataCommittedDuringProviderCallAndSkipsItInUpdateCount() {
        var rows = repository.saveAllAndFlush(List.of(entity(SYMBOL, bar(FROM, null, null)), entity(SYMBOL, bar(TO, null, null))));
        when(provider.getDailyPriceHistory(REQUEST)).thenAnswer(invocation -> {
            jdbc.update("UPDATE daily_price_bar SET trading_value_krw = ?, trading_venue_scope = ? WHERE id = ?",
                    100L, SCOPE.name(), rows.getFirst().getId());
            return history(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE));
        });

        var result = service.backfill(REQUEST, SCOPE);

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.targetCount()).isEqualTo(2);
        assertThat(storedBars()).containsExactly(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE));
    }

    @Test
    void doesNotOverwriteConflictingConcurrentFillOrCommitOtherRows() {
        var rows = repository.saveAllAndFlush(List.of(entity(SYMBOL, bar(FROM, null, null)), entity(SYMBOL, bar(TO, null, null))));
        when(provider.getDailyPriceHistory(REQUEST)).thenAnswer(invocation -> {
            jdbc.update("UPDATE daily_price_bar SET trading_value_krw = ?, trading_venue_scope = ? WHERE id = ?",
                    999L, SCOPE.name(), rows.getLast().getId());
            return history(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE));
        });

        assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE)).isInstanceOf(IllegalStateException.class);

        assertThat(storedBars()).containsExactly(bar(FROM, null, null), bar(TO, 999L, SCOPE));
    }

    @Test
    void refreshesStalePersistenceContextBeforeApplyingUpdates() {
        var rows = repository.saveAllAndFlush(List.of(entity(SYMBOL, bar(FROM, null, null)), entity(SYMBOL, bar(TO, null, null))));
        when(provider.getDailyPriceHistory(REQUEST)).thenReturn(history(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE)));
        DailyPriceBarRepository staleRepository = mock(DailyPriceBarRepository.class);
        when(staleRepository.findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(SYMBOL, FROM, TO)).thenReturn(rows);
        when(staleRepository.findAllForTradingValueBackfill(SYMBOL, FROM, TO)).thenAnswer(invocation -> {
            List<DailyPriceBarEntity> cached = repository.findAllForTradingValueBackfill(SYMBOL, FROM, TO);
            jdbc.update("UPDATE daily_price_bar SET trading_value_krw = ?, trading_venue_scope = ? WHERE id = ?",
                    999L, SCOPE.name(), cached.getLast().getId());
            return cached;
        });
        var staleService = new DailyPriceTradingValueBackfillService(provider, staleRepository, transactionManager, entityManager);

        assertThatThrownBy(() -> staleService.backfill(REQUEST, SCOPE)).isInstanceOf(IllegalStateException.class);

        // The conflicting write joined the failed transaction and was rolled back too.
        assertThat(storedBars()).containsExactly(bar(FROM, null, null), bar(TO, null, null));
    }

    @Test
    void serializesConcurrentBackfillsAndReportsOnlyOneCommittedBatch() throws Exception {
        repository.saveAllAndFlush(List.of(entity(SYMBOL, bar(FROM, null, null)), entity(SYMBOL, bar(TO, null, null))));
        CountDownLatch fetched = new CountDownLatch(2);
        when(provider.getDailyPriceHistory(REQUEST)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            fetched.countDown();
            if (!fetched.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent fetch did not arrive.");
            }
            return history(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE));
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.backfill(REQUEST, SCOPE));
            var second = executor.submit(() -> service.backfill(REQUEST, SCOPE));
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(results).extracting(result -> result.status()).containsExactlyInAnyOrder(
                    DailyPriceTradingValueBackfillStatus.BACKFILLED, DailyPriceTradingValueBackfillStatus.ALREADY_FILLED);
            assertThat(results).extracting(result -> result.updatedCount()).containsExactlyInAnyOrder(2, 0);
        }
        assertThat(storedBars()).containsExactly(bar(FROM, 100L, SCOPE), bar(TO, 300L, SCOPE));
        verify(provider, times(2)).getDailyPriceHistory(REQUEST);
    }

    @Test
    void rejectsExistingTransactionWithoutCallingProvider() {
        var transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> assertThatThrownBy(() -> service.backfill(REQUEST, SCOPE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside"));

        verifyNoInteractions(provider);
    }

    private List<DailyPriceBar> storedBars() {
        return repository.findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(SYMBOL, FROM, TO)
                .stream().map(DailyPriceBarEntity::toBar).toList();
    }

    private DailyPriceBarEntity entity(String symbol, DailyPriceBar bar) {
        return DailyPriceBarEntity.from(symbol, bar);
    }

    private DailyPriceHistory history(DailyPriceBar... bars) {
        return new DailyPriceHistory(SYMBOL, List.of(bars));
    }

    private DailyPriceBar bar(LocalDate date, Long value, TradingVenueScope scope) {
        return new DailyPriceBar(date, 100, 120, 80, 110, 1000, value, scope);
    }
}
