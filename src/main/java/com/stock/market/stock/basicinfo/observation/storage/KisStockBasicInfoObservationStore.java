package com.stock.market.stock.basicinfo.observation.storage;

import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
}
