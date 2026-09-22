package com.stock.broker.kis.account.provider;

import com.stock.broker.account.BrokerAccountSnapshot;
import com.stock.broker.account.provider.BrokerAccountProvider;
import com.stock.broker.kis.account.KisAccountBalanceClient;
import com.stock.broker.kis.account.dto.KisAccountBalanceResponse;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisProperties;

import java.time.Clock;
import java.util.Objects;

public class KisBrokerAccountProvider implements BrokerAccountProvider {
    private final KisAccountBalanceClient accountBalanceClient;
    private final KisTokenProvider tokenProvider;
    private final KisProperties properties;
    private final Clock clock;

    public KisBrokerAccountProvider(
            KisAccountBalanceClient accountBalanceClient,
            KisTokenProvider tokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        this.accountBalanceClient = Objects.requireNonNull(
                accountBalanceClient,
                "accountBalanceClient must not be null."
        );
        this.tokenProvider = Objects.requireNonNull(
                tokenProvider,
                "tokenProvider must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null."
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Override
    public BrokerAccountSnapshot getAccountSnapshot() {
        String accessToken = tokenProvider.getAccessToken();
        KisAccountBalanceResponse response = Objects.requireNonNull(
                accountBalanceClient.getAccountBalance(
                        properties.accountNumber(),
                        properties.accountProductCode(),
                        accessToken
                ),
                "KIS account balance response must not be null."
        );

        return response.toSnapshot(clock.instant());
    }
}
