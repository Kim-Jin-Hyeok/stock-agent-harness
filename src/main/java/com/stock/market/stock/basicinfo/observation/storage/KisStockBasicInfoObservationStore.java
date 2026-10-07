package com.stock.market.stock.basicinfo.observation.storage;

import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

@Component
public class KisStockBasicInfoObservationStore {
    private final KisStockBasicInfoObservationRepository repository;
    private final Clock clock;

    public KisStockBasicInfoObservationStore(KisStockBasicInfoObservationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Transactional
    public Long save(KisStockBasicInfoRawResponse response) {
        Objects.requireNonNull(response, "response must not be null.");
        // Each received response is a new observation, even if its symbol or content is unchanged.
        var entity = KisStockBasicInfoObservationEntity.from(response, clock.instant());
        return repository.saveAndFlush(entity).getId();
    }

    @Transactional(readOnly = true)
    public Optional<KisStockBasicInfoRawResponse> findById(Long id) {
        Objects.requireNonNull(id, "id must not be null.");
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive.");
        }
        return repository.findById(id).map(KisStockBasicInfoObservationEntity::toRawResponse);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findLatestObservationId(String symbol, Instant evaluatedAt) {
        if (symbol == null || !symbol.matches("[0-9A-Z]{6}")) {
            throw new IllegalArgumentException("symbol must be exactly 6 uppercase alphanumeric characters.");
        }
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        // Stored times have microsecond precision; rounding the cutoff up could include future rows.
        var cutoff = evaluatedAt.truncatedTo(ChronoUnit.MICROS);
        return repository.findObservationIdsAvailableAt(symbol, cutoff, PageRequest.of(0, 1)).stream().findFirst();
    }
}
