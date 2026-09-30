package com.stock.market.index.history.query;

import com.stock.market.index.history.MarketIndexDailyHistory;
import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.MarketIndexDailyObservation;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class MarketIndexDailyHistoryQueryService {
    private final MarketIndexDailyObservationRepository repository;

    public MarketIndexDailyHistoryQueryService(
            MarketIndexDailyObservationRepository repository
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository must not be null."
        );
    }

    public MarketIndexDailyHistory getDailyHistory(
            MarketIndexDailyHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        List<MarketIndexDailyObservation> observations = repository
                .findAllByBenchmarkIdAndObservationDateBetweenOrderByObservationDateAsc(
                        request.benchmarkId(),
                        request.fromDate(),
                        request.toDate()
                )
                .stream()
                .map(entity -> entity.toObservation())
                .toList();

        return new MarketIndexDailyHistory(
                request.benchmarkId(),
                observations
        );
    }
}
