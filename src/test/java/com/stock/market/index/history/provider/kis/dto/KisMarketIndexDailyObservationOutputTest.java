package com.stock.market.index.history.provider.kis.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.index.history.MarketIndexDailyObservation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KisMarketIndexDailyObservationOutputTest {

    @Test
    void deserializesOfficialKisFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisMarketIndexDailyObservationOutput output =
                objectMapper.readValue(
                        """
                                {
                                  "stck_bsop_date": "20260930",
                                  "bstp_nmix_prpr": "3421.37",
                                  "bstp_nmix_oprc": "3400.10",
                                  "bstp_nmix_hgpr": "3430.25",
                                  "bstp_nmix_lwpr": "3398.75"
                                }
                                """,
                        KisMarketIndexDailyObservationOutput.class
                );

        assertThat(output.observationDate()).isEqualTo("20260930");
        assertThat(output.closeValue()).isEqualTo("3421.37");
    }

    @Test
    void convertsOutputWithoutLosingDecimalPrecision() {
        MarketIndexDailyObservation observation = validOutput()
                .toObservation();

        assertThat(observation.observationDate())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(observation.closeValue())
                .isEqualByComparingTo(new BigDecimal("3421.37"));
    }

    @Test
    void rejectsInvalidObservationDate() {
        KisMarketIndexDailyObservationOutput output =
                new KisMarketIndexDailyObservationOutput(
                        "2026-09-30",
                        "3421.37"
                );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toObservation)
                .withMessage(
                        "observationDate must use yyyyMMdd format."
                )
                .withCauseInstanceOf(
                        java.time.format.DateTimeParseException.class
                );
    }

    @Test
    void rejectsNonNumericCloseValue() {
        KisMarketIndexDailyObservationOutput output =
                new KisMarketIndexDailyObservationOutput(
                        "20260930",
                        "invalid"
                );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toObservation)
                .withMessage("closeValue must be a number.")
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void rejectsNonPositiveCloseValue() {
        KisMarketIndexDailyObservationOutput output =
                new KisMarketIndexDailyObservationOutput(
                        "20260930",
                        "0"
                );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toObservation)
                .withMessage("closeValue must be positive.");
    }

    private KisMarketIndexDailyObservationOutput validOutput() {
        return new KisMarketIndexDailyObservationOutput(
                "20260930",
                "3421.37"
        );
    }
}
