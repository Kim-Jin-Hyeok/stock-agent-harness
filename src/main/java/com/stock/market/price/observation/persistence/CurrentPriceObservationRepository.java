package com.stock.market.price.observation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurrentPriceObservationRepository
        extends JpaRepository<CurrentPriceObservationEntity, Long> {
    List<CurrentPriceObservationEntity> findAllByRunIdOrderByObservedAtAsc(
            String runId
    );
}
