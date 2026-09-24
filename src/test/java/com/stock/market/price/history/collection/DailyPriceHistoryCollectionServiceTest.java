package com.stock.market.price.history.collection;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.persistence.DailyPriceBarEntity;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyPriceHistoryCollectionServiceTest {
    private static final String SYMBOL = "005930";
    private static final LocalDate FROM_DATE = LocalDate.of(2023, 9, 25);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 25);
    private static final DailyPriceHistoryRequest REQUEST =
            new DailyPriceHistoryRequest(SYMBOL, FROM_DATE, TO_DATE);

    @Test
    void collectsRequestedRangeWhenHistoryDoesNotExist() {
        Dependencies dependencies = dependencies();
        List<DailyPriceBar> bars = List.of(
                bar(FROM_DATE),
                bar(TO_DATE)
        );
        when(dependencies.repository()
                .findTopBySymbolOrderByTradingDateDesc(SYMBOL))
                .thenReturn(Optional.empty());
        when(dependencies.provider().getDailyPriceHistory(REQUEST))
                .thenReturn(new DailyPriceHistory(SYMBOL, bars));

        DailyPriceHistoryCollectionResult result = dependencies.service()
                .collect(REQUEST);

        assertThat(result.status())
                .isEqualTo(DailyPriceHistoryCollectionStatus.COLLECTED);
        assertThat(result.actualFromDate()).isEqualTo(FROM_DATE);
        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
        verify(dependencies.provider()).getDailyPriceHistory(REQUEST);
        verify(dependencies.repository()).saveAll(anyList());
    }

    @Test
    void collectsOnlyDatesAfterLatestStoredBar() {
        Dependencies dependencies = dependencies();
        LocalDate latestDate = LocalDate.of(2026, 9, 23);
        DailyPriceHistoryRequest incrementalRequest =
                new DailyPriceHistoryRequest(
                        SYMBOL,
                        latestDate.plusDays(1),
                        TO_DATE
                );
        when(dependencies.repository()
                .findTopBySymbolOrderByTradingDateDesc(SYMBOL))
                .thenReturn(Optional.of(entity(latestDate)));
        when(dependencies.provider().getDailyPriceHistory(
                incrementalRequest
        )).thenReturn(new DailyPriceHistory(SYMBOL, List.of(
                bar(latestDate.plusDays(1)),
                bar(TO_DATE)
        )));

        DailyPriceHistoryCollectionResult result = dependencies.service()
                .collect(REQUEST);

        assertThat(result.status())
                .isEqualTo(DailyPriceHistoryCollectionStatus.COLLECTED);
        assertThat(result.actualFromDate())
                .isEqualTo(latestDate.plusDays(1));
        assertThat(result.fetchedCount()).isEqualTo(2);
        verify(dependencies.provider()).getDailyPriceHistory(
                incrementalRequest
        );
    }

    @Test
    void skipsProviderCallWhenHistoryIsAlreadyUpToDate() {
        Dependencies dependencies = dependencies();
        when(dependencies.repository()
                .findTopBySymbolOrderByTradingDateDesc(SYMBOL))
                .thenReturn(Optional.of(entity(TO_DATE)));

        DailyPriceHistoryCollectionResult result = dependencies.service()
                .collect(REQUEST);

        assertThat(result.status()).isEqualTo(
                DailyPriceHistoryCollectionStatus.ALREADY_UP_TO_DATE
        );
        assertThat(result.actualFromDate()).isNull();
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.savedCount()).isZero();
        verifyNoInteractions(dependencies.provider());
        verify(dependencies.repository(), never()).saveAll(anyList());
    }

    @Test
    void doesNotSaveWhenProviderReturnsNoBars() {
        Dependencies dependencies = dependencies();
        when(dependencies.repository()
                .findTopBySymbolOrderByTradingDateDesc(SYMBOL))
                .thenReturn(Optional.empty());
        when(dependencies.provider().getDailyPriceHistory(REQUEST))
                .thenReturn(new DailyPriceHistory(SYMBOL, List.of()));

        DailyPriceHistoryCollectionResult result = dependencies.service()
                .collect(REQUEST);

        assertThat(result.status())
                .isEqualTo(DailyPriceHistoryCollectionStatus.COLLECTED);
        assertThat(result.fetchedCount()).isZero();
        assertThat(result.savedCount()).isZero();
        verify(dependencies.repository(), never()).saveAll(anyList());
    }

    @Test
    void rejectsHistoryForDifferentSymbol() {
        Dependencies dependencies = dependencies();
        when(dependencies.repository()
                .findTopBySymbolOrderByTradingDateDesc(SYMBOL))
                .thenReturn(Optional.empty());
        when(dependencies.provider().getDailyPriceHistory(REQUEST))
                .thenReturn(new DailyPriceHistory(
                        "000660",
                        List.of(bar(TO_DATE))
                ));

        assertThatThrownBy(() -> dependencies.service().collect(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "collected history symbol must match request symbol."
                );
        verify(dependencies.repository(), never()).saveAll(anyList());
    }

    private Dependencies dependencies() {
        DailyPriceHistoryProvider provider = mock(
                DailyPriceHistoryProvider.class
        );
        DailyPriceBarRepository repository = mock(
                DailyPriceBarRepository.class
        );
        return new Dependencies(
                provider,
                repository,
                new DailyPriceHistoryCollectionService(provider, repository)
        );
    }

    private DailyPriceBarEntity entity(LocalDate tradingDate) {
        return DailyPriceBarEntity.from(SYMBOL, bar(tradingDate));
    }

    private DailyPriceBar bar(LocalDate tradingDate) {
        return new DailyPriceBar(
                tradingDate,
                70_000L,
                73_000L,
                69_000L,
                72_000L,
                1_000_000L
        );
    }

    private record Dependencies(
            DailyPriceHistoryProvider provider,
            DailyPriceBarRepository repository,
            DailyPriceHistoryCollectionService service
    ) {
    }
}
