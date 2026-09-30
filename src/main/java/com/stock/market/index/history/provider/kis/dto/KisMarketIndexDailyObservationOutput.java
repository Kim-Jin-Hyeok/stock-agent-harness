package com.stock.market.index.history.provider.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.market.index.history.MarketIndexDailyObservation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisMarketIndexDailyObservationOutput(
        @JsonProperty("stck_bsop_date") String observationDate,
        @JsonProperty("bstp_nmix_prpr") String closeValue
) {
    public MarketIndexDailyObservation toObservation() {
        return new MarketIndexDailyObservation(
                parseObservationDate(),
                parseCloseValue()
        );
    }

    private LocalDate parseObservationDate() {
        if (observationDate == null || observationDate.isBlank()) {
            throw new IllegalArgumentException(
                    "observationDate must not be blank."
            );
        }
        try {
            return LocalDate.parse(
                    observationDate,
                    DateTimeFormatter.BASIC_ISO_DATE
            );
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "observationDate must use yyyyMMdd format.",
                    exception
            );
        }
    }

    private BigDecimal parseCloseValue() {
        if (closeValue == null || closeValue.isBlank()) {
            throw new IllegalArgumentException(
                    "closeValue must not be blank."
            );
        }
        try {
            return new BigDecimal(closeValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "closeValue must be a number.",
                    exception
            );
        }
    }
}
