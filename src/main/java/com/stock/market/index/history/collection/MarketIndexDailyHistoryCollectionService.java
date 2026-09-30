package com.stock.market.index.history.collection;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationEntity;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import com.stock.market.index.history.provider.MarketIndexDailyHistoryProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MarketIndexDailyHistoryCollectionService {
    private final MarketIndexDailyHistoryProvider historyProvider;
    private final MarketIndexDailyObservationRepository repository;

    public MarketIndexDailyHistoryCollectionService(
            MarketIndexDailyHistoryProvider historyProvider,
            MarketIndexDailyObservationRepository repository
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

    public MarketIndexDailyHistoryCollectionResult collect(
            MarketIndexDailyHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        Optional<MarketIndexDailyObservationEntity> latest = repository
                .findTopByBenchmarkIdOrderByObservationDateDesc(
                        request.benchmarkId()
                );
        if (latest.isPresent()
                && !latest.get().getObservationDate()
                        .isBefore(request.toDate())) {
            return MarketIndexDailyHistoryCollectionResult
                    .alreadyUpToDate(request);
        }

        LocalDate actualFromDate = latest
                .map(MarketIndexDailyObservationEntity::getObservationDate)
                .map(date -> date.plusDays(1))
                .filter(date -> date.isAfter(request.fromDate()))
                .orElse(request.fromDate());
        MarketIndexDailyHistoryRequest collectionRequest =
                new MarketIndexDailyHistoryRequest(
                        request.benchmarkId(),
                        actualFromDate,
                        request.toDate()
                );
        MarketIndexDailyHistory history = Objects.requireNonNull(
                historyProvider.getDailyHistory(collectionRequest),
                "collected history must not be null."
        );
        validateHistory(history, collectionRequest);

        List<MarketIndexDailyObservationEntity> entities = history
                .observations()
                .stream()
                .map(observation -> MarketIndexDailyObservationEntity.from(
                        request.benchmarkId(),
                        observation
                ))
                .toList();
        if (!entities.isEmpty()) {
            repository.saveAll(entities);
        }

        return MarketIndexDailyHistoryCollectionResult.collected(
                request,
                actualFromDate,
                entities.size()
        );
    }

    private void validateHistory(
            MarketIndexDailyHistory history,
            MarketIndexDailyHistoryRequest request
    ) {
        if (!request.benchmarkId().equals(history.benchmarkId())) {
            throw new IllegalStateException(
                    "collected history benchmarkId must match request "
                            + "benchmarkId."
            );
        }
        for (MarketIndexDailyObservation observation :
                history.observations()) {
            if (observation.observationDate().isBefore(request.fromDate())
                    || observation.observationDate()
                            .isAfter(request.toDate())) {
                throw new IllegalStateException(
                        "collected observation must be within requested "
                                + "range."
                );
            }
        }
    }
}
