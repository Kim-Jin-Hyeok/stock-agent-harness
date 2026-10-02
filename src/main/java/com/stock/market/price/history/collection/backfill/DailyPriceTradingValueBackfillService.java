package com.stock.market.price.history.collection.backfill;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.collection.backfill.result.DailyPriceTradingValueBackfillResult;
import com.stock.market.price.history.collection.backfill.result.DailyPriceTradingValueBackfillStatus;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DailyPriceTradingValueBackfillService {
    private final DailyPriceHistoryProvider historyProvider;
    private final DailyPriceBarRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public DailyPriceTradingValueBackfillService(
            DailyPriceHistoryProvider historyProvider,
            DailyPriceBarRepository repository,
            PlatformTransactionManager transactionManager,
            EntityManager entityManager
    ) {
        this.historyProvider = Objects.requireNonNull(historyProvider, "historyProvider must not be null.");
        this.repository = Objects.requireNonNull(repository, "repository must not be null.");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null.");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null.");
        readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setReadOnly(true);
        writeTransaction = new TransactionTemplate(transactionManager);
    }

    public DailyPriceTradingValueBackfillResult backfill(
            DailyPriceHistoryRequest request,
            TradingVenueScope expectedVenueScope
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(expectedVenueScope, "expectedVenueScope must not be null.");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Backfill must be called outside an existing transaction.");
        }

        List<StoredBar> stored = Objects.requireNonNull(readTransaction.execute(status ->
                repository.findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                                request.symbol(), request.fromDate(), request.toDate())
                        .stream().map(entity -> new StoredBar(entity.getId(), entity.toBar())).toList()));
        for (StoredBar row : stored) {
            if (row.bar().tradingVenueScope() != null
                    && row.bar().tradingVenueScope() != expectedVenueScope) {
                throw new IllegalStateException("Stored venue must match expected venue. date="
                        + row.bar().tradingDate());
            }
        }
        List<StoredBar> targets = stored.stream().filter(StoredBar::hasMissingMetadata).toList();
        if (targets.isEmpty()) {
            return new DailyPriceTradingValueBackfillResult(
                    DailyPriceTradingValueBackfillStatus.NO_TARGETS,
                    request, null, expectedVenueScope, 0, 0, 0);
        }

        DailyPriceHistoryRequest collectionRange = new DailyPriceHistoryRequest(
                request.symbol(), targets.getFirst().bar().tradingDate(),
                targets.getLast().bar().tradingDate());
        List<StoredBar> checkedRows = stored.stream()
                .filter(row -> !row.bar().tradingDate().isBefore(collectionRange.fromDate())
                        && !row.bar().tradingDate().isAfter(collectionRange.toDate()))
                .toList();

        // No DB transaction or row lock is held during the external request.
        DailyPriceHistory history = Objects.requireNonNull(
                historyProvider.getDailyPriceHistory(collectionRange), "backfill history must not be null.");
        Map<LocalDate, DailyPriceBar> fetched = validateHistory(history, collectionRange, expectedVenueScope);
        for (StoredBar row : checkedRows) {
            DailyPriceBarEntity.from(request.symbol(), row.bar())
                    .validateTradingValueBackfill(requiredBar(fetched, row.bar().tradingDate()));
        }

        int updatedCount = Objects.requireNonNull(writeTransaction.execute(status ->
                applyBackfill(collectionRange, checkedRows, fetched)));
        return new DailyPriceTradingValueBackfillResult(
                updatedCount == 0 ? DailyPriceTradingValueBackfillStatus.ALREADY_FILLED
                        : DailyPriceTradingValueBackfillStatus.BACKFILLED,
                request, collectionRange, expectedVenueScope,
                targets.size(), history.bars().size(), updatedCount);
    }

    private Map<LocalDate, DailyPriceBar> validateHistory(
            DailyPriceHistory history,
            DailyPriceHistoryRequest request,
            TradingVenueScope expectedVenueScope
    ) {
        if (!request.symbol().equals(history.symbol())) {
            throw new IllegalStateException("Backfill history symbol must match requested symbol.");
        }
        for (DailyPriceBar bar : history.bars()) {
            if (bar.tradingDate().isBefore(request.fromDate()) || bar.tradingDate().isAfter(request.toDate())) {
                throw new IllegalStateException("Backfill bar must be within collection range.");
            }
            if (bar.tradingValueKrw() == null || bar.tradingVenueScope() != expectedVenueScope) {
                throw new IllegalStateException("Backfill value must be known and venue must match expected venue. date="
                        + bar.tradingDate());
            }
        }
        return history.bars().stream().collect(Collectors.toMap(DailyPriceBar::tradingDate, Function.identity()));
    }

    private int applyBackfill(
            DailyPriceHistoryRequest range,
            List<StoredBar> originalRows,
            Map<LocalDate, DailyPriceBar> fetched
    ) {
        List<DailyPriceBarEntity> locked = repository.findAllForTradingValueBackfill(
                range.symbol(), range.fromDate(), range.toDate());
        // Refresh also protects callers with a previously populated persistence context.
        locked.forEach(entity -> entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE));
        Map<LocalDate, DailyPriceBarEntity> current = locked.stream()
                .collect(Collectors.toMap(DailyPriceBarEntity::getTradingDate, Function.identity()));
        for (StoredBar original : originalRows) {
            DailyPriceBarEntity entity = current.get(original.bar().tradingDate());
            if (entity == null || !original.id().equals(entity.getId())) {
                throw new IllegalStateException("Stored backfill row was removed or replaced. date="
                        + original.bar().tradingDate());
            }
            DailyPriceBar currentBar = entity.toBar();
            DailyPriceBarEntity.from(range.symbol(), original.bar()).validateOhlcv(currentBar);
            if ((original.bar().tradingValueKrw() != null
                    && !original.bar().tradingValueKrw().equals(currentBar.tradingValueKrw()))
                    || (original.bar().tradingVenueScope() != null
                    && original.bar().tradingVenueScope() != currentBar.tradingVenueScope())) {
                throw new IllegalStateException("Previously known backfill metadata changed. date="
                        + currentBar.tradingDate());
            }
            entity.validateTradingValueBackfill(requiredBar(fetched, currentBar.tradingDate()));
        }

        int updatedCount = 0;
        for (StoredBar original : originalRows) {
            if (original.hasMissingMetadata()
                    && current.get(original.bar().tradingDate())
                    .fillMissingTradingValueMetadata(requiredBar(fetched, original.bar().tradingDate()))) {
                updatedCount++;
            }
        }
        if (updatedCount > 0) {
            repository.flush();
        }
        return updatedCount;
    }

    private DailyPriceBar requiredBar(Map<LocalDate, DailyPriceBar> fetched, LocalDate date) {
        DailyPriceBar bar = fetched.get(date);
        if (bar == null) {
            throw new IllegalStateException("Backfill response is missing a stored date. date=" + date);
        }
        return bar;
    }

    private record StoredBar(Long id, DailyPriceBar bar) {
        private boolean hasMissingMetadata() {
            return bar.tradingValueKrw() == null || bar.tradingVenueScope() == null;
        }
    }
}
