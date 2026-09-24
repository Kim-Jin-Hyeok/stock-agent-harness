package com.stock.market.price.history.collection.runner;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionResult;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionService;
import com.stock.market.price.history.collection.config.DailyPriceHistoryBootstrapProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationArguments;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyPriceHistoryBootstrapRunnerTest {
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 23);
    private static final LocalDate FROM_DATE = LocalDate.of(2023, 9, 23);

    @Test
    void doesNotCollectWhenDisabled() {
        DailyPriceHistoryCollectionService collectionService = mock(
                DailyPriceHistoryCollectionService.class
        );
        DailyPriceCollectionDatePolicy collectionDatePolicy = mock(
                DailyPriceCollectionDatePolicy.class
        );
        DailyPriceHistoryBootstrapRunner runner = runner(
                collectionService,
                collectionDatePolicy,
                false,
                List.of("005930")
        );

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(collectionService, collectionDatePolicy);
    }

    @Test
    void collectsConfiguredSymbolsUsingLatestCompletedTradingDate() {
        DailyPriceHistoryCollectionService collectionService = mock(
                DailyPriceHistoryCollectionService.class
        );
        DailyPriceCollectionDatePolicy collectionDatePolicy = mock(
                DailyPriceCollectionDatePolicy.class
        );
        when(collectionDatePolicy.getLatestCompletedTradingDate())
                .thenReturn(TO_DATE);
        when(collectionService.collect(any()))
                .thenAnswer(invocation -> {
                    DailyPriceHistoryRequest request = invocation.getArgument(0);
                    return DailyPriceHistoryCollectionResult
                            .alreadyUpToDate(request);
                });
        DailyPriceHistoryBootstrapRunner runner = runner(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("005930", "000660")
        );
        DailyPriceHistoryRequest firstRequest = request("005930");
        DailyPriceHistoryRequest secondRequest = request("000660");

        runner.run(mock(ApplicationArguments.class));

        InOrder inOrder = inOrder(collectionService);
        inOrder.verify(collectionService).collect(firstRequest);
        inOrder.verify(collectionService).collect(secondRequest);
    }

    @Test
    void stopsWhenCollectionFails() {
        DailyPriceHistoryCollectionService collectionService = mock(
                DailyPriceHistoryCollectionService.class
        );
        DailyPriceCollectionDatePolicy collectionDatePolicy = mock(
                DailyPriceCollectionDatePolicy.class
        );
        DailyPriceHistoryRequest firstRequest = request("005930");
        DailyPriceHistoryRequest secondRequest = request("000660");
        when(collectionDatePolicy.getLatestCompletedTradingDate())
                .thenReturn(TO_DATE);
        when(collectionService.collect(firstRequest))
                .thenThrow(new IllegalStateException("KIS collection failed."));
        DailyPriceHistoryBootstrapRunner runner = runner(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("005930", "000660")
        );

        assertThatThrownBy(() ->
                runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("KIS collection failed.");

        verify(collectionService).collect(firstRequest);
        verify(collectionService, never()).collect(secondRequest);
    }

    private DailyPriceHistoryBootstrapRunner runner(
            DailyPriceHistoryCollectionService collectionService,
            DailyPriceCollectionDatePolicy collectionDatePolicy,
            boolean enabled,
            List<String> symbols
    ) {
        return new DailyPriceHistoryBootstrapRunner(
                collectionService,
                collectionDatePolicy,
                new DailyPriceHistoryBootstrapProperties(
                        enabled,
                        symbols,
                        3
                )
        );
    }

    private DailyPriceHistoryRequest request(String symbol) {
        return new DailyPriceHistoryRequest(symbol, FROM_DATE, TO_DATE);
    }
}
