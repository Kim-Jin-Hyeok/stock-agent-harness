package com.stock.market;

import com.stock.market.session.MarketSessionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class MarketService {
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MarketSessionPolicy marketSessionPolicy;
    private final Clock clock;

    public MarketSnapshot getCurrentSnapshot() {
        LocalDateTime evaluatedAt = LocalDateTime.ofInstant(
                clock.instant(),
                KOREA_ZONE_ID
        );
        boolean marketOpen = marketSessionPolicy.isOpen(evaluatedAt);

        return new MarketSnapshot(
                "KR",
                marketOpen,
                marketOpen
                        ? "Korean regular market is open."
                        : "Korean regular market is closed."
        );
    }
}
