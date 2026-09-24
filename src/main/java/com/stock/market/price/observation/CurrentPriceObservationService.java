package com.stock.market.price.observation;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.market.price.observation.persistence.CurrentPriceObservationEntity;
import com.stock.market.price.observation.persistence.CurrentPriceObservationRepository;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CurrentPriceObservationService {
    private final CurrentPriceObservationRepository repository;

    public CurrentPriceObservation record(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            CurrentPriceSnapshot snapshot,
            CurrentPriceLookupSource source
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        CurrentPriceObservation observation = new CurrentPriceObservation(
                null,
                runId,
                strategyIdentity,
                snapshot.symbol(),
                snapshot.priceKrw(),
                snapshot.observedAt(),
                source
        );
        CurrentPriceObservationEntity saved = Objects.requireNonNull(
                repository.save(
                        CurrentPriceObservationEntity.from(observation)
                ),
                "saved observation must not be null."
        );
        return saved.toObservation();
    }

    public void clear() {
        repository.deleteAll();
    }
}
