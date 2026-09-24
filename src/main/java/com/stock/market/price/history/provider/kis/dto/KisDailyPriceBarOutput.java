package com.stock.market.price.history.provider.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.market.price.history.DailyPriceBar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisDailyPriceBarOutput(
        @JsonProperty("stck_bsop_date") String tradingDate,
        @JsonProperty("stck_oprc") String openPrice,
        @JsonProperty("stck_hgpr") String highPrice,
        @JsonProperty("stck_lwpr") String lowPrice,
        @JsonProperty("stck_clpr") String closePrice,
        @JsonProperty("acml_vol") String accumulatedVolume
) {
    public DailyPriceBar toBar() {
        return new DailyPriceBar(
                parseTradingDate(),
                parseLong(openPrice, "openPrice"),
                parseLong(highPrice, "highPrice"),
                parseLong(lowPrice, "lowPrice"),
                parseLong(closePrice, "closePrice"),
                parseLong(accumulatedVolume, "accumulatedVolume")
        );
    }

    private LocalDate parseTradingDate() {
        if (tradingDate == null || tradingDate.isBlank()) {
            throw new IllegalArgumentException(
                    "tradingDate must not be blank."
            );
        }

        try {
            return LocalDate.parse(
                    tradingDate,
                    DateTimeFormatter.BASIC_ISO_DATE
            );
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "tradingDate must use yyyyMMdd format.",
                    exception
            );
        }
    }

    private long parseLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank."
            );
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must be a number.",
                    exception
            );
        }
    }
}
