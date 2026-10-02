package com.stock.market.price.history.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bar from DailyPriceBarEntity bar
            where bar.symbol = :symbol
                and bar.tradingDate between :fromDate and :toDate
            order by bar.tradingDate asc
            """)
    List<DailyPriceBarEntity> findAllForTradingValueBackfill(
            @Param("symbol") String symbol,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    Optional<DailyPriceBarEntity> findTopBySymbolOrderByTradingDateDesc(
            String symbol
    );

    Optional<DailyPriceBarEntity>
    findTopBySymbolAndTradingDateAfterOrderByTradingDateAsc(
            String symbol,
            LocalDate tradingDate
    );

    List<DailyPriceBarEntity> findAllBySymbolOrderByTradingDateDesc(
            String symbol,
            Pageable pageable
    );

    List<DailyPriceBarEntity>
    findAllBySymbolAndTradingDateLessThanEqualOrderByTradingDateDesc(
            String symbol,
            LocalDate tradingDate,
            Pageable pageable
    );
}
