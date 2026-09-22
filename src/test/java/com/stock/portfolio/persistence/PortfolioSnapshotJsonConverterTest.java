package com.stock.portfolio.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioSnapshotJsonConverterTest {
    private final PortfolioSnapshotJsonConverter converter =
            new PortfolioSnapshotJsonConverter(
                    new ObjectMapper().findAndRegisterModules()
            );

    @Test
    void convertsPortfolioSnapshotToJsonAndBack() {
        PortfolioSnapshot snapshot = portfolioSnapshot();

        String json = converter.toJson(snapshot);
        PortfolioSnapshot restored = converter.fromJson(json);

        assertThat(restored).isEqualTo(snapshot);
    }

    @Test
    void throwsWhenPortfolioSnapshotJsonIsInvalid() {
        assertThatThrownBy(() -> converter.fromJson("invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to deserialize portfolio snapshot json.");
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                9_300_000L,
                10_000_000L,
                List.of(new PortfolioPosition(
                        "005930",
                        10L,
                        70_000L,
                        700_000L
                ))
        );
    }
}
