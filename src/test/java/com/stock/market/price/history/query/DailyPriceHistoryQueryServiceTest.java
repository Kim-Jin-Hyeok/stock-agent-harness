package com.stock.market.price.history.query;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyPriceHistoryQueryServiceTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 23);
    private static final DailyPriceHistoryRequest REQUEST =
            new DailyPriceHistoryRequest(SYMBOL, FROM_DATE, TO_DATE);

    @Test
    void returnsDailyPriceHistoryFromStoredBars() {
        DailyPriceBarRepository repository = mock(
                DailyPriceBarRepository.class
        );
        DailyPriceHistoryQueryService service =
                new DailyPriceHistoryQueryService(repository);
        DailyPriceBar firstBar = bar(FROM_DATE, 72_000L);
        DailyPriceBar secondBar = bar(TO_DATE, 73_000L);
        when(repository
                .findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                        SYMBOL,
                        FROM_DATE,
                        TO_DATE
                ))
                .thenReturn(List.of(
                        DailyPriceBarEntity.from(SYMBOL, firstBar),
                        DailyPriceBarEntity.from(SYMBOL, secondBar)
                ));

        DailyPriceHistory history = service.getDailyPriceHistory(REQUEST);

        assertThat(history.symbol()).isEqualTo(SYMBOL);
        assertThat(history.bars()).containsExactly(firstBar, secondBar);
        verify(repository)
                .findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                        SYMBOL,
                        FROM_DATE,
                        TO_DATE
                );
    }

    @Test
    void returnsEmptyHistoryWhenStoredBarsDoNotExist() {
        DailyPriceBarRepository repository = mock(
                DailyPriceBarRepository.class
        );
        DailyPriceHistoryQueryService service =
                new DailyPriceHistoryQueryService(repository);
        when(repository
                .findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                        SYMBOL,
                        FROM_DATE,
                        TO_DATE
                ))
                .thenReturn(List.of());

        DailyPriceHistory history = service.getDailyPriceHistory(REQUEST);

        assertThat(history.symbol()).isEqualTo(SYMBOL);
        assertThat(history.bars()).isEmpty();
    }

    @Test
    void rejectsNullRequest() {
        DailyPriceBarRepository repository = mock(
                DailyPriceBarRepository.class
        );
        DailyPriceHistoryQueryService service =
                new DailyPriceHistoryQueryService(repository);

        assertThatThrownBy(() -> service.getDailyPriceHistory(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null.");
        verifyNoInteractions(repository);
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
