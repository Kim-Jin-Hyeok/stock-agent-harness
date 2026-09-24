package com.stock.market;

import com.stock.market.calendar.MarketCalendarProperties;
import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.market.session.MarketSessionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MarketServiceTest {

    @Test
    void returnsOpenSnapshotDuringKoreanRegularMarketSession() {
        MarketService service = serviceAt("2026-01-02T00:00:00Z");

        MarketSnapshot snapshot = service.getCurrentSnapshot();

        assertThat(snapshot.market()).isEqualTo("KR");
        assertThat(snapshot.marketOpen()).isTrue();
        assertThat(snapshot.description()).isEqualTo(
                "Korean regular market is open."
        );
    }

    @Test
    void returnsClosedSnapshotOutsideKoreanRegularMarketSession() {
        MarketService service = serviceAt("2026-01-02T06:30:00Z");

        MarketSnapshot snapshot = service.getCurrentSnapshot();

        assertThat(snapshot.market()).isEqualTo("KR");
        assertThat(snapshot.marketOpen()).isFalse();
        assertThat(snapshot.description()).isEqualTo(
                "Korean regular market is closed."
        );
    }

    private MarketService serviceAt(String instant) {
        MarketTradingDayPolicy tradingDayPolicy = new MarketTradingDayPolicy(
                new MarketCalendarProperties(Set.of())
        );
        MarketSessionPolicy sessionPolicy = new MarketSessionPolicy(tradingDayPolicy);
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new MarketService(sessionPolicy, clock);
    }
}
