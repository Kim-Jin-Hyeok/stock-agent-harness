package com.stock.market.index.history.collection.backfill;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.collection.backfill.result.MarketIndexDailyHistoryBackfillResult;
import com.stock.market.index.history.collection.backfill.result.MarketIndexDailyHistoryBackfillStatus;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationEntity;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import com.stock.market.index.history.provider.MarketIndexDailyHistoryProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MarketIndexDailyHistoryBackfillService {
    private final MarketIndexDailyHistoryProvider historyProvider;
    private final MarketIndexDailyObservationRepository repository;

    public MarketIndexDailyHistoryBackfillService(
            MarketIndexDailyHistoryProvider historyProvider,
            MarketIndexDailyObservationRepository repository
    ) {
        this.historyProvider = Objects.requireNonNull(
                historyProvider, "historyProvider must not be null."
        );
        this.repository = Objects.requireNonNull(
                repository, "repository must not be null."
        );
    }

    public MarketIndexDailyHistoryBackfillResult backfill(
            MarketIndexDailyHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        var earliest = repository.findTopByBenchmarkIdOrderByObservationDateAsc(
                request.benchmarkId()
        );
        if (earliest.isPresent()
                && !earliest.get().getObservationDate().isAfter(request.fromDate())) {
            return new MarketIndexDailyHistoryBackfillResult(
                    MarketIndexDailyHistoryBackfillStatus.NO_EARLIER_RANGE,
                    request, null, 0, 0, null, null
            );
        }

        LocalDate actualToDate = earliest
                .map(MarketIndexDailyObservationEntity::getObservationDate)
                .map(date -> date.minusDays(1))
                .filter(date -> date.isBefore(request.toDate()))
                .orElse(request.toDate());
        MarketIndexDailyHistoryRequest collectionRange =
                new MarketIndexDailyHistoryRequest(
                        request.benchmarkId(), request.fromDate(), actualToDate
                );
        MarketIndexDailyHistory history = Objects.requireNonNull(
                historyProvider.getDailyHistory(collectionRange),
                "collected history must not be null."
        );
        validateHistory(history, collectionRange);
        List<MarketIndexDailyObservation> observations = history.observations();
        if (observations.isEmpty()) {
            return new MarketIndexDailyHistoryBackfillResult(
                    MarketIndexDailyHistoryBackfillStatus.NO_DATA,
                    request, collectionRange, 0, 0, null, null
            );
        }

        // Recheck after the provider call so existing observations are preserved.
        Set<LocalDate> storedDates = repository
                .findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                        request.benchmarkId(), collectionRange.fromDate(), actualToDate
                ).stream()
                .map(MarketIndexDailyObservationEntity::getObservationDate)
                .collect(Collectors.toSet());
        List<MarketIndexDailyObservationEntity> entities = observations.stream()
                .filter(observation -> !storedDates.contains(observation.observationDate()))
                .map(observation -> MarketIndexDailyObservationEntity.from(
                        request.benchmarkId(), observation
                ))
                .toList();
        if (!entities.isEmpty()) {
            repository.saveAllAndFlush(entities);
        }

        return new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.BACKFILLED,
                request, collectionRange, observations.size(), entities.size(),
                observations.getFirst().observationDate(),
                observations.getLast().observationDate()
        );
    }

    private void validateHistory(
            MarketIndexDailyHistory history,
            MarketIndexDailyHistoryRequest request
    ) {
        if (!request.benchmarkId().equals(history.benchmarkId())) {
            throw new IllegalStateException(
                    "collected history benchmarkId must match backfill request."
            );
        }
        for (MarketIndexDailyObservation observation : history.observations()) {
            if (observation.observationDate().isBefore(request.fromDate())
                    || observation.observationDate().isAfter(request.toDate())) {
                throw new IllegalStateException(
                        "collected observation must be within backfill range."
                );
            }
        }
    }
}
