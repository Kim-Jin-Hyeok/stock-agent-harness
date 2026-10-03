package com.stock.strategy.universe.eligibility.request;

import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockEligibilityRequestTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 23);
    private static final Instant CUTOFF = Instant.parse("2026-09-23T09:00:00Z");

    @Test
    void defensivelyCopiesExplicitMarketsAndTypesWithoutExpandingThem() {
        Set<StockMarket> markets = new HashSet<>(Set.of(StockMarket.KOSDAQ));
        Set<StockSecurityType> types = new HashSet<>(Set.of(StockSecurityType.PREFERRED_STOCK));
        StockEligibilityRequest request = new StockEligibilityRequest(DATE, CUTOFF, markets, types);

        markets.add(StockMarket.KOSPI);
        types.add(StockSecurityType.COMMON_STOCK);
        assertThat(request.selectionAsOfDate()).isEqualTo(DATE);
        assertThat(request.selectionCutoffAt()).isEqualTo(CUTOFF);
        assertThat(request.eligibleMarkets()).containsExactly(StockMarket.KOSDAQ);
        assertThat(request.eligibleSecurityTypes()).containsExactly(StockSecurityType.PREFERRED_STOCK);
        assertThatThrownBy(() -> request.eligibleMarkets().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.eligibleSecurityTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowsBothDomesticMarketsAndIndividualStockTypesWhenExplicitlyRequested() {
        StockEligibilityRequest request = new StockEligibilityRequest(
                DATE, CUTOFF, Set.of(StockMarket.KOSPI, StockMarket.KOSDAQ),
                Set.of(StockSecurityType.COMMON_STOCK, StockSecurityType.PREFERRED_STOCK)
        );

        assertThat(request.eligibleMarkets()).hasSize(2);
        assertThat(request.eligibleSecurityTypes()).hasSize(2);
    }

    @Test
    void acceptsCutoffAtStartOfSelectionDateInKoreaAndLaterCutoff() {
        Instant koreanMidnight = Instant.parse("2026-09-22T15:00:00Z");

        assertThat(request(DATE, koreanMidnight).selectionCutoffAt()).isEqualTo(koreanMidnight);
        assertThat(request(DATE, CUTOFF.plusSeconds(86400)).selectionCutoffAt())
                .isEqualTo(CUTOFF.plusSeconds(86400));
    }

    @Test
    void rejectsCutoffBeforeSelectionDateInKorea() {
        assertThatThrownBy(() -> request(DATE, Instant.parse("2026-09-22T14:59:59.999999999Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionCutoffAt must not be before selectionAsOfDate in Asia/Seoul.");
    }

    @Test
    void requiresDateAndCutoffInsteadOfUsingCurrentTime() {
        assertThatThrownBy(() -> request(null, CUTOFF))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selectionAsOfDate must not be null.");
        assertThatThrownBy(() -> request(DATE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("selectionCutoffAt must not be null.");
    }

    @Test
    void rejectsMissingOrEmptyMarkets() {
        assertThatThrownBy(() -> new StockEligibilityRequest(
                DATE, CUTOFF, null, Set.of(StockSecurityType.COMMON_STOCK)
        )).isInstanceOf(NullPointerException.class).hasMessage("eligibleMarkets must not be null.");
        assertThatThrownBy(() -> new StockEligibilityRequest(
                DATE, CUTOFF, Set.of(), Set.of(StockSecurityType.COMMON_STOCK)
        )).isInstanceOf(IllegalArgumentException.class).hasMessage("eligibleMarkets must not be empty.");
    }

    @Test
    void rejectsMissingOrEmptyTypes() {
        assertThatThrownBy(() -> new StockEligibilityRequest(DATE, CUTOFF, Set.of(StockMarket.KOSPI), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eligibleSecurityTypes must not be null.");
        assertThatThrownBy(() -> new StockEligibilityRequest(DATE, CUTOFF, Set.of(StockMarket.KOSPI), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eligibleSecurityTypes must not be empty.");
    }

    @Test
    void rejectsNullSetElementsRatherThanTreatingThemAsUnknownInput() {
        Set<StockMarket> markets = new HashSet<>(Set.of(StockMarket.KOSPI));
        markets.add(null);
        Set<StockSecurityType> types = new HashSet<>(Set.of(StockSecurityType.COMMON_STOCK));
        types.add(null);

        assertThatThrownBy(() -> new StockEligibilityRequest(
                DATE, CUTOFF, markets, Set.of(StockSecurityType.COMMON_STOCK)
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StockEligibilityRequest(DATE, CUTOFF, Set.of(StockMarket.KOSPI), types))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsUnsupportedMarketInAllowedSet() {
        assertThatThrownBy(() -> new StockEligibilityRequest(
                DATE, CUTOFF, Set.of(StockMarket.KOSPI, StockMarket.OTHER),
                Set.of(StockSecurityType.COMMON_STOCK)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eligibleMarkets must contain only KOSPI or KOSDAQ.");
    }

    @ParameterizedTest
    @EnumSource(value = StockSecurityType.class, names = {"ETF", "ETN", "OTHER"})
    void rejectsUnsupportedTypeEvenAlongsideAnAllowedIndividualStock(StockSecurityType type) {
        assertThatThrownBy(() -> new StockEligibilityRequest(
                DATE, CUTOFF, Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK, type)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eligibleSecurityTypes must contain only supported individual stock types.");
    }

    private StockEligibilityRequest request(LocalDate date, Instant cutoff) {
        return new StockEligibilityRequest(
                date, cutoff, Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK)
        );
    }
}
