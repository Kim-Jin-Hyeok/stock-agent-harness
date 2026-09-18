package com.stock.market.price.provider.kis.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class KisCurrentPriceOutputTest {
    private static final String SYMBOL = "005930";
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void deserializesKisCurrentPriceField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisCurrentPriceOutput output = objectMapper.readValue(
                "{\"stck_prpr\":\"72000\"}",
                KisCurrentPriceOutput.class
        );

        assertThat(output.currentPrice()).isEqualTo("72000");
    }

    @Test
    void convertsCurrentPriceToSnapshot() {
        KisCurrentPriceOutput output = new KisCurrentPriceOutput("72000");

        CurrentPriceSnapshot snapshot = output.toSnapshot(SYMBOL, OBSERVED_AT);

        assertThat(snapshot.symbol()).isEqualTo(SYMBOL);
        assertThat(snapshot.priceKrw()).isEqualTo(72_000L);
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void rejectsMissingSymbol() {
        KisCurrentPriceOutput output = new KisCurrentPriceOutput("72000");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> output.toSnapshot(" ", OBSERVED_AT))
                .withMessage("symbol must not be blank.");
    }

    @Test
    void rejectsMissingObservedAt() {
        KisCurrentPriceOutput output = new KisCurrentPriceOutput("72000");

        assertThatNullPointerException()
                .isThrownBy(() -> output.toSnapshot(SYMBOL, null))
                .withMessage("observedAt must not be null.");
    }

    @Test
    void rejectsMissingCurrentPrice() {
        KisCurrentPriceOutput output = new KisCurrentPriceOutput(" ");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> output.toSnapshot(SYMBOL, OBSERVED_AT))
                .withMessage("currentPrice must not be blank.");
    }

    @Test
    void rejectsNonNumericCurrentPrice() {
        KisCurrentPriceOutput output = new KisCurrentPriceOutput("invalid");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> output.toSnapshot(SYMBOL, OBSERVED_AT))
                .withMessage("currentPrice must be a number.")
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void rejectsNonPositiveCurrentPrice() {
        KisCurrentPriceOutput output = new KisCurrentPriceOutput("0");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> output.toSnapshot(SYMBOL, OBSERVED_AT))
                .withMessage("currentPrice must be positive.");
    }
}
