package com.stock.harness.persistence;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessDailyPriceHistorySnapshotTest {

    @Test
    void fromConvertsDailyPriceHistory() {
        DailyPriceBar bar = new DailyPriceBar(
                LocalDate.of(2026, 1, 2),
                69_000L,
                71_000L,
                68_000L,
                70_000L,
                1_000_000L
        );

        HarnessDailyPriceHistorySnapshot snapshot =
                HarnessDailyPriceHistorySnapshot.from(
                        new DailyPriceHistory("005930", List.of(bar))
                );

        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.bars()).containsExactly(
                HarnessDailyPriceBarSnapshot.from(bar)
        );
    }

    @Test
    void copiesSourceBars() {
        List<HarnessDailyPriceBarSnapshot> bars = new ArrayList<>();

        HarnessDailyPriceHistorySnapshot snapshot =
                new HarnessDailyPriceHistorySnapshot("005930", bars);
        bars.add(new HarnessDailyPriceBarSnapshot(
                LocalDate.of(2026, 1, 2),
                69_000L,
                71_000L,
                68_000L,
                70_000L,
                1_000_000L
        ));

        assertThat(snapshot.bars()).isEmpty();
        assertThatThrownBy(() -> snapshot.bars().add(bars.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
