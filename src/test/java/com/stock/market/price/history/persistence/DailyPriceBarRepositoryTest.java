package com.stock.market.price.history.persistence;

import com.stock.market.price.history.DailyPriceBar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class DailyPriceBarRepositoryTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate TRADING_DATE =
            LocalDate.of(2026, 9, 24);

    @Autowired
    private DailyPriceBarRepository repository;

    @Test
    void savesAndRestoresDailyPriceBar() {
        DailyPriceBar expected = bar(TRADING_DATE, 72_000L);

        DailyPriceBarEntity saved = repository.saveAndFlush(
                DailyPriceBarEntity.from(SYMBOL, expected)
        );
        DailyPriceBarEntity restored = repository.findById(saved.getId())
                .orElseThrow();

        assertThat(restored.getSymbol()).isEqualTo(SYMBOL);
        assertThat(restored.toBar()).isEqualTo(expected);
    }

    @Test
    void findsBarsInRequestedRangeOrderedByTradingDate() {
        repository.saveAllAndFlush(List.of(
                entity(SYMBOL, TRADING_DATE, 72_000L),
                entity(SYMBOL, TRADING_DATE.minusDays(2), 70_000L),
                entity(SYMBOL, TRADING_DATE.minusDays(1), 71_000L),
                entity(SYMBOL, TRADING_DATE.minusDays(3), 69_000L),
                entity("000660", TRADING_DATE.minusDays(1), 200_000L)
        ));

        List<DailyPriceBarEntity> result = repository
                .findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                        SYMBOL,
                        TRADING_DATE.minusDays(2),
                        TRADING_DATE
                );

        assertThat(result)
                .extracting(DailyPriceBarEntity::getTradingDate)
                .containsExactly(
                        TRADING_DATE.minusDays(2),
                        TRADING_DATE.minusDays(1),
                        TRADING_DATE
                );
    }

    @Test
    void findsLatestBarForSymbol() {
        repository.saveAllAndFlush(List.of(
                entity(SYMBOL, TRADING_DATE.minusDays(1), 71_000L),
                entity(SYMBOL, TRADING_DATE, 72_000L),
                entity("000660", TRADING_DATE.plusDays(1), 200_000L)
        ));

        DailyPriceBarEntity latest = repository
                .findTopBySymbolOrderByTradingDateDesc(SYMBOL)
                .orElseThrow();

        assertThat(latest.getTradingDate()).isEqualTo(TRADING_DATE);
        assertThat(latest.getClosePriceKrw()).isEqualTo(72_000L);
    }

    @Test
    void findsLimitedLatestBarsForSymbol() {
        repository.saveAllAndFlush(List.of(
                entity(SYMBOL, TRADING_DATE.minusDays(3), 69_000L),
                entity(SYMBOL, TRADING_DATE.minusDays(2), 70_000L),
                entity(SYMBOL, TRADING_DATE.minusDays(1), 71_000L),
                entity(SYMBOL, TRADING_DATE, 72_000L),
                entity("000660", TRADING_DATE.plusDays(1), 200_000L)
        ));

        List<DailyPriceBarEntity> result = repository
                .findAllBySymbolOrderByTradingDateDesc(
                        SYMBOL,
                        PageRequest.of(0, 3)
                );

        assertThat(result)
                .extracting(DailyPriceBarEntity::getTradingDate)
                .containsExactly(
                        TRADING_DATE,
                        TRADING_DATE.minusDays(1),
                        TRADING_DATE.minusDays(2)
                );
    }

    @Test
    void rejectsDuplicateSymbolAndTradingDate() {
        repository.saveAndFlush(entity(SYMBOL, TRADING_DATE, 72_000L));

        assertThatThrownBy(() -> repository.saveAndFlush(
                entity(SYMBOL, TRADING_DATE, 73_000L)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesSameTradingDateForDifferentSymbols() {
        repository.saveAllAndFlush(List.of(
                entity(SYMBOL, TRADING_DATE, 72_000L),
                entity("000660", TRADING_DATE, 200_000L)
        ));

        assertThat(repository.count()).isEqualTo(2L);
    }

    private DailyPriceBarEntity entity(
            String symbol,
            LocalDate tradingDate,
            long closePriceKrw
    ) {
        return DailyPriceBarEntity.from(
                symbol,
                bar(tradingDate, closePriceKrw)
        );
    }

    private DailyPriceBar bar(
            LocalDate tradingDate,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                tradingDate,
                closePriceKrw - 1_000L,
                closePriceKrw + 1_000L,
                closePriceKrw - 2_000L,
                closePriceKrw,
                1_000_000L
        );
    }
}
