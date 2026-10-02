package com.stock.market.price.history.provider.kis.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.TradingVenueScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KisDailyPriceBarOutputTest {

    @Test
    void deserializesKisDailyPriceFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        KisDailyPriceBarOutput output = objectMapper.readValue(
                """
                        {
                          "stck_bsop_date": "20260923",
                          "stck_oprc": "70000",
                          "stck_hgpr": "73000",
                          "stck_lwpr": "69000",
                          "stck_clpr": "72000",
                          "acml_vol": "1234567",
                          "acml_tr_pbmn": "87654321"
                        }
                        """,
                KisDailyPriceBarOutput.class
        );

        assertThat(output.tradingDate()).isEqualTo("20260923");
        assertThat(output.openPrice()).isEqualTo("70000");
        assertThat(output.highPrice()).isEqualTo("73000");
        assertThat(output.lowPrice()).isEqualTo("69000");
        assertThat(output.closePrice()).isEqualTo("72000");
        assertThat(output.accumulatedVolume()).isEqualTo("1234567");
        assertThat(output.accumulatedTradingValue()).isEqualTo("87654321");
        assertThat(output.toBar().tradingValueKrw()).isEqualTo(87_654_321L);
    }

    @Test
    void convertsOutputToDailyPriceBar() {
        DailyPriceBar bar = validOutput().toBar();

        assertThat(bar.tradingDate()).isEqualTo(
                LocalDate.of(2026, 9, 23)
        );
        assertThat(bar.openPriceKrw()).isEqualTo(70_000L);
        assertThat(bar.highPriceKrw()).isEqualTo(73_000L);
        assertThat(bar.lowPriceKrw()).isEqualTo(69_000L);
        assertThat(bar.closePriceKrw()).isEqualTo(72_000L);
        assertThat(bar.volume()).isEqualTo(1_234_567L);
        assertThat(bar.tradingValueKrw()).isNull();
        assertThat(bar.tradingVenueScope()).isNull();
    }

    @Test
    void preservesRawTradingValueWithoutPriceVolumeApproximationOrScaling() {
        DailyPriceBar bar = outputWithTradingValue("87654321000")
                .toBar(TradingVenueScope.INTEGRATED);

        assertThat(bar.tradingValueKrw()).isEqualTo(87_654_321_000L);
        assertThat(bar.tradingValueKrw()).isNotEqualTo(
                bar.closePriceKrw() * bar.volume()
        );
        assertThat(bar.tradingVenueScope()).isEqualTo(TradingVenueScope.INTEGRATED);
    }

    @Test
    void preservesConfirmedZeroTradingValue() {
        assertThat(outputWithTradingValue("0").toBar().tradingValueKrw()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void preservesMissingTradingValueAsUnknown(String value) {
        DailyPriceBar bar = outputWithTradingValue(value)
                .toBar(TradingVenueScope.KRX);

        assertThat(bar.tradingValueKrw()).isNull();
        assertThat(bar.tradingVenueScope()).isEqualTo(TradingVenueScope.KRX);
    }

    @Test
    void deserializesLegacyResponseWithoutTradingValue() throws Exception {
        KisDailyPriceBarOutput output = new ObjectMapper().readValue("""
                {
                  "stck_bsop_date": "20260923",
                  "stck_oprc": "70000",
                  "stck_hgpr": "73000",
                  "stck_lwpr": "69000",
                  "stck_clpr": "72000",
                  "acml_vol": "1234567"
                }
                """, KisDailyPriceBarOutput.class);

        assertThat(output.accumulatedTradingValue()).isNull();
        assertThat(output.toBar().tradingValueKrw()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1.5", "1,000", "9223372036854775808"})
    void rejectsMalformedOrOverflowingTradingValue(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> outputWithTradingValue(value).toBar())
                .withMessage("accumulatedTradingValue must be a number.")
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void rejectsNegativeTradingValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> outputWithTradingValue("-1").toBar())
                .withMessage("tradingValueKrw must not be negative.");
    }

    private KisDailyPriceBarOutput outputWithTradingValue(String value) {
        return new KisDailyPriceBarOutput("20260923", "70000", "73000",
                "69000", "72000", "1234567", value);
    }

    @Test
    void rejectsBlankTradingDate() {
        KisDailyPriceBarOutput output = output(
                " ",
                "70000",
                "73000",
                "69000",
                "72000",
                "1234567"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toBar)
                .withMessage("tradingDate must not be blank.");
    }

    @Test
    void rejectsInvalidTradingDateFormat() {
        KisDailyPriceBarOutput output = output(
                "2026-09-23",
                "70000",
                "73000",
                "69000",
                "72000",
                "1234567"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toBar)
                .withMessage("tradingDate must use yyyyMMdd format.")
                .withCauseInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @Test
    void rejectsBlankPrice() {
        KisDailyPriceBarOutput output = output(
                "20260923",
                " ",
                "73000",
                "69000",
                "72000",
                "1234567"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toBar)
                .withMessage("openPrice must not be blank.");
    }

    @Test
    void rejectsNonNumericPrice() {
        KisDailyPriceBarOutput output = output(
                "20260923",
                "70000",
                "73000",
                "69000",
                "invalid",
                "1234567"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toBar)
                .withMessage("closePrice must be a number.")
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void rejectsNegativeVolume() {
        KisDailyPriceBarOutput output = output(
                "20260923",
                "70000",
                "73000",
                "69000",
                "72000",
                "-1"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toBar)
                .withMessage("volume must not be negative.");
    }

    @Test
    void rejectsInvalidHighAndLowPriceRelationship() {
        KisDailyPriceBarOutput output = output(
                "20260923",
                "70000",
                "71000",
                "69000",
                "72000",
                "1234567"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(output::toBar)
                .withMessage(
                        "highPriceKrw must not be lower than another price."
                );
    }

    private KisDailyPriceBarOutput validOutput() {
        return output(
                "20260923",
                "70000",
                "73000",
                "69000",
                "72000",
                "1234567"
        );
    }

    private KisDailyPriceBarOutput output(
            String tradingDate,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String accumulatedVolume
    ) {
        return new KisDailyPriceBarOutput(
                tradingDate,
                openPrice,
                highPrice,
                lowPrice,
                closePrice,
                accumulatedVolume
        );
    }
}
