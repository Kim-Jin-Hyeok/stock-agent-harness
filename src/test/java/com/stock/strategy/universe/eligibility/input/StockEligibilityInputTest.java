package com.stock.strategy.universe.eligibility.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockEligibilityInputTest {
    @Test
    void preservesSymbolAndExplicitSourceFieldsWithoutInferringVerification() {
        LocalDate date = LocalDate.of(2026, 9, 23);
        Instant availableAt = Instant.parse("2026-09-23T06:30:00Z");
        StockEligibilityInput input = new StockEligibilityInput(
                "005930", date, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, "synthetic-source-01", availableAt,
                StockEligibilityEvidenceStatus.UNVERIFIED
        );

        assertThat(input.symbol()).isEqualTo("005930");
        assertThat(input.asOfDate()).isEqualTo(date);
        assertThat(input.market()).isEqualTo(StockMarket.KOSPI);
        assertThat(input.securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(input.listingStatus()).isEqualTo(StockListingStatus.LISTED);
        assertThat(input.sourceReference()).isEqualTo("synthetic-source-01");
        assertThat(input.informationAvailableAt()).isEqualTo(availableAt);
        assertThat(input.evidenceStatus()).isEqualTo(StockEligibilityEvidenceStatus.UNVERIFIED);
    }

    @Test
    void retainsMissingMetadataInsteadOfSubstitutingCurrentOrDefaultValues() {
        StockEligibilityInput input = new StockEligibilityInput(
                "000660", null, null, null, null, null, null, null
        );

        assertThat(input.symbol()).isEqualTo("000660");
        assertThat(input.asOfDate()).isNull();
        assertThat(input.market()).isNull();
        assertThat(input.securityType()).isNull();
        assertThat(input.listingStatus()).isNull();
        assertThat(input.sourceReference()).isNull();
        assertThat(input.informationAvailableAt()).isNull();
        assertThat(input.evidenceStatus()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsMissingSymbol(String symbol) {
        assertThatThrownBy(() -> new StockEligibilityInput(
                symbol, null, null, null, null, null, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }
}
