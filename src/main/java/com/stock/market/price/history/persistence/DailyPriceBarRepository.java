package com.stock.market.price.history.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceBarRepository
        extends JpaRepository<DailyPriceBarEntity, Long> {
    List<DailyPriceBarEntity>
    findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
            String symbol,
            LocalDate fromDate,
            LocalDate toDate
    );

    Optional<DailyPriceBarEntity> findTopBySymbolOrderByTradingDateDesc(
            String symbol
    );

    List<DailyPriceBarEntity> findAllBySymbolOrderByTradingDateDesc(
            String symbol,
            Pageable pageable
    );
}
