package com.stock.market.price.history.collection;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DailyPriceHistoryCollectionService {
    private final DailyPriceHistoryProvider historyProvider;
    private final DailyPriceBarRepository repository;

    public DailyPriceHistoryCollectionService(
            DailyPriceHistoryProvider historyProvider,
            DailyPriceBarRepository repository
    ) {
        this.historyProvider = Objects.requireNonNull(
                historyProvider,
                "historyProvider must not be null."
        );
        this.repository = Objects.requireNonNull(
                repository,
                "repository must not be null."
        );
    }

    public DailyPriceHistoryCollectionResult collect(
            DailyPriceHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        Optional<DailyPriceBarEntity> latest = repository
                .findTopBySymbolOrderByTradingDateDesc(request.symbol());
        if (latest.isPresent()
                && !latest.get().getTradingDate().isBefore(request.toDate())) {
            return DailyPriceHistoryCollectionResult.alreadyUpToDate(request);
        }

        LocalDate actualFromDate = latest
                .map(DailyPriceBarEntity::getTradingDate)
                .map(date -> date.plusDays(1))
                .filter(date -> date.isAfter(request.fromDate()))
                .orElse(request.fromDate());
        DailyPriceHistoryRequest collectionRequest =
                new DailyPriceHistoryRequest(
                        request.symbol(),
                        actualFromDate,
                        request.toDate()
                );
        DailyPriceHistory history = Objects.requireNonNull(
                historyProvider.getDailyPriceHistory(collectionRequest),
                "collected history must not be null."
        );
        validateHistory(history, collectionRequest);

        List<DailyPriceBarEntity> entities = history.bars().stream()
                .map(bar -> DailyPriceBarEntity.from(
                        request.symbol(),
                        bar
                ))
                .toList();
        if (!entities.isEmpty()) {
            repository.saveAll(entities);
        }

        return DailyPriceHistoryCollectionResult.collected(
                request,
                actualFromDate,
                entities.size()
        );
    }

    private void validateHistory(
            DailyPriceHistory history,
            DailyPriceHistoryRequest request
    ) {
        if (!request.symbol().equals(history.symbol())) {
            throw new IllegalStateException(
                    "collected history symbol must match request symbol."
            );
        }
        for (DailyPriceBar bar : history.bars()) {
            if (bar.tradingDate().isBefore(request.fromDate())
                    || bar.tradingDate().isAfter(request.toDate())) {
                throw new IllegalStateException(
                        "collected bar must be within requested range."
                );
            }
        }
    }
}
