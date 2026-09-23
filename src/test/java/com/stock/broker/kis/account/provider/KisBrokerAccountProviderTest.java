package com.stock.broker.kis.account.provider;

import com.stock.broker.account.BrokerAccountPosition;
import com.stock.broker.account.BrokerAccountSnapshot;
import com.stock.broker.kis.account.KisAccountBalanceClient;
import com.stock.broker.kis.account.dto.KisAccountBalancePositionOutput;
import com.stock.broker.kis.account.dto.KisAccountBalanceResponse;
import com.stock.broker.kis.account.dto.KisAccountBalanceSummaryOutput;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KisBrokerAccountProviderTest {
    private static final String ACCOUNT_NUMBER = "12345678";
    private static final String ACCOUNT_PRODUCT_CODE = "01";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-22T05:00:00Z");

    @Test
    void getsTokenAndMapsAccountBalanceToSnapshot() {
        KisAccountBalanceClient accountBalanceClient = mock(
                KisAccountBalanceClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(accountBalanceClient.getAccountBalance(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        )).thenReturn(successfulResponse());
        when(clock.instant()).thenReturn(OBSERVED_AT);
        KisBrokerAccountProvider provider = provider(
                accountBalanceClient,
                tokenProvider,
                clock
        );

        BrokerAccountSnapshot snapshot = provider.getAccountSnapshot();

        assertThat(snapshot.depositAmountKrw()).isEqualTo(9_280_000L);
        assertThat(snapshot.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(snapshot.positions()).containsExactly(
                new BrokerAccountPosition("005930", 10L, 70_000L, 720_000L)
        );
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);

        InOrder inOrder = inOrder(tokenProvider, accountBalanceClient, clock);
        inOrder.verify(tokenProvider).getAccessToken();
        inOrder.verify(accountBalanceClient).getAccountBalance(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        );
        inOrder.verify(clock).instant();
    }

    @Test
    void rejectsFailedAccountBalanceResponse() {
        KisAccountBalanceClient accountBalanceClient = mock(
                KisAccountBalanceClient.class
        );
        KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
        Clock clock = mock(Clock.class);
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(accountBalanceClient.getAccountBalance(
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                ACCESS_TOKEN
        )).thenReturn(new KisAccountBalanceResponse(
                "1",
                "EGW00123",
                "Request failed.",
                List.of(),
                List.of(),
                "",
                ""
        ));
        when(clock.instant()).thenReturn(OBSERVED_AT);
        KisBrokerAccountProvider provider = provider(
                accountBalanceClient,
                tokenProvider,
                clock
        );

        assertThatIllegalStateException()
                .isThrownBy(provider::getAccountSnapshot)
                .withMessage(
                        "KIS account balance response was not successful. "
                                + "messageCode=EGW00123"
                );
    }

    private KisBrokerAccountProvider provider(
            KisAccountBalanceClient accountBalanceClient,
            KisTokenProvider tokenProvider,
            Clock clock
    ) {
        return new KisBrokerAccountProvider(
                accountBalanceClient,
                tokenProvider,
                properties(),
                clock
        );
    }

    private KisAccountBalanceResponse successfulResponse() {
        return new KisAccountBalanceResponse(
                "0",
                "KIOK0560",
                "Request completed successfully.",
                List.of(new KisAccountBalancePositionOutput(
                        "005930",
                        "10",
                        "70000.0000",
                        "720000"
                )),
                List.of(new KisAccountBalanceSummaryOutput(
                        "9280000",
                        "10000000"
                )),
                "",
                ""
        );
    }

    private KisProperties properties() {
        return new KisProperties(
                true,
                URI.create("https://openapivts.koreainvestment.com:29443"),
                "test-app-key",
                "test-app-secret",
                ACCOUNT_NUMBER,
                ACCOUNT_PRODUCT_CODE,
                Duration.ofMinutes(1),
                10,
                10
        );
    }
}
