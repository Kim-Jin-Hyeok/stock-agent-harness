package com.stock.strategy.universe.candidate.evaluation.runner.config;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.stock.strategy.universe.candidate.evaluation.runner.support.StockCandidateEvaluationRunnerFixture.disabledProperties;
import static com.stock.strategy.universe.candidate.evaluation.runner.support.StockCandidateEvaluationRunnerFixture.properties;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.CUTOFF;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATES;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCandidateEvaluationPropertiesTest {
    @Test
    void doesNotRequireEvaluationCriteriaWhenDisabled() {
        var properties = disabledProperties();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.targetSymbols()).isEmpty();
        assertThat(properties.requiredTradingDates()).isEmpty();
        assertThat(properties.eligibleMarkets()).isEmpty();
        assertThat(properties.eligibleSecurityTypes()).isEmpty();
        assertThat(properties.eligibilityInputs()).isEmpty();
        assertThat(properties.selectionCutoffAt()).isNull();
        assertThat(properties.minimumAverageTradingValueKrw()).isNull();
        assertThat(properties.maxCandidateCount()).isNull();
        assertThatThrownBy(properties::toRequest).isInstanceOf(IllegalStateException.class)
                .hasMessage("Manual stock candidate evaluation must be enabled to create a request.");
    }

    @ParameterizedTest
    @EnumSource(TradingVenueScope.class)
    void preservesExplicitCriteriaAndUsesExistingRequestValidation(TradingVenueScope venue) {
        var cutoff = CUTOFF.plusNanos(123456789);
        var properties = new StockCandidateEvaluationProperties(true, List.of("005930", "000660"), DATE,
                cutoff, Set.of(StockMarket.KOSPI, StockMarket.KOSDAQ), Set.of(StockSecurityType.COMMON_STOCK),
                DATES, venue, 123L, 7, List.of(eligible("005930")));

        var request = properties.toRequest();

        assertThat(request.targetSymbols()).containsExactly("000660", "005930");
        assertThat(request.eligibilityRequest().selectionAsOfDate()).isEqualTo(DATE);
        assertThat(request.liquidityRequest().selectionAsOfDate()).isEqualTo(DATE);
        assertThat(request.eligibilityRequest().selectionCutoffAt()).isEqualTo(cutoff);
        assertThat(request.eligibilityRequest().eligibleMarkets()).containsExactlyInAnyOrder(StockMarket.KOSPI, StockMarket.KOSDAQ);
        assertThat(request.eligibilityRequest().eligibleSecurityTypes()).containsExactly(StockSecurityType.COMMON_STOCK);
        assertThat(request.liquidityRequest().requiredTradingDates()).isEqualTo(DATES);
        assertThat(request.liquidityRequest().expectedVenueScope()).isEqualTo(venue);
        assertThat(request.liquidityRequest().minimumAverageTradingValueKrw()).isEqualTo(123L);
        assertThat(request.liquidityRequest().maxCandidateCount()).isEqualTo(7);
        assertThat(properties.eligibilityInputs()).containsExactly(eligible("005930"));
        assertThat(properties.toRequest()).isEqualTo(request);
    }

    @Test
    void defensivelyCopiesCollectionsWithoutChangingCallerOrder() {
        var targets = new ArrayList<>(List.of("005930", "000660"));
        var dates = new ArrayList<>(DATES);
        var markets = new HashSet<>(Set.of(StockMarket.KOSPI));
        var types = new HashSet<>(Set.of(StockSecurityType.COMMON_STOCK));
        var inputs = new ArrayList<>(List.of(eligible("005930"), eligible("000660")));
        var properties = new StockCandidateEvaluationProperties(true, targets, DATE, CUTOFF, markets, types,
                dates, TradingVenueScope.INTEGRATED, 100L, 2, inputs);

        assertThat(targets).containsExactly("005930", "000660");
        assertThat(inputs).containsExactly(eligible("005930"), eligible("000660"));
        targets.clear();
        dates.clear();
        markets.clear();
        types.clear();
        inputs.clear();

        assertThat(properties.toRequest()).isEqualTo(request("005930", "000660"));
        assertThat(properties.eligibilityInputs()).containsExactly(eligible("005930"), eligible("000660"));
        assertThatThrownBy(properties.targetSymbols()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(properties.requiredTradingDates()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(properties.eligibleMarkets()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(properties.eligibleSecurityTypes()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(properties.eligibilityInputs()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowsMissingEligibilityWithoutInventingMetadataOrEvidenceStatus() {
        var noInputs = properties(request("005930"), null);
        var missingMetadata = new StockEligibilityInput("005930", null, null, null, null, null, null, null);
        var explicitUnknown = properties(request("005930"), List.of(missingMetadata));

        assertThat(noInputs.eligibilityInputs()).isEmpty();
        assertThat(noInputs.toRequest()).isEqualTo(request("005930"));
        assertThat(explicitUnknown.eligibilityInputs()).containsExactly(missingMetadata);
        assertThat(explicitUnknown.eligibilityInputs().getFirst().evidenceStatus()).isNull();
        assertThat(explicitUnknown.eligibilityInputs().getFirst().asOfDate()).isNull();
        assertThat(explicitUnknown.eligibilityInputs().getFirst().informationAvailableAt()).isNull();
        assertThat(explicitUnknown.eligibilityInputs().getFirst().sourceReference()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"targets", "date", "cutoff", "markets", "types", "dates", "venue", "minimum", "maximum"})
    void requiresEveryEvaluationCriterionWhenEnabled(String missing) {
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true,
                missing.equals("targets") ? null : List.of("005930"), missing.equals("date") ? null : DATE,
                missing.equals("cutoff") ? null : CUTOFF,
                missing.equals("markets") ? null : Set.of(StockMarket.KOSPI),
                missing.equals("types") ? null : Set.of(StockSecurityType.COMMON_STOCK),
                missing.equals("dates") ? null : DATES, missing.equals("venue") ? null : TradingVenueScope.INTEGRATED,
                missing.equals("minimum") ? null : 100L, missing.equals("maximum") ? null : 2, null))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsNonPositiveMinimum(long minimum) {
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true, List.of("005930"), DATE, CUTOFF,
                Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK), DATES,
                TradingVenueScope.INTEGRATED, minimum, 2, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("minimumAverageTradingValueKrw must be positive.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveMaximum(int maximum) {
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true, List.of("005930"), DATE, CUTOFF,
                Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK), DATES,
                TradingVenueScope.INTEGRATED, 100L, maximum, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("maxCandidateCount must be positive.");
    }

    @Test
    void rejectsInvalidEligibilityInputStructureButNotMissingEvidence() {
        assertThatThrownBy(() -> properties(request("005930"), List.of(eligible("005930"), eligible("005930"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate eligibility input symbol: 005930");
        assertThatThrownBy(() -> properties(request("005930"), List.of(eligible("000660"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eligibility input symbol must belong to targetSymbols: 000660");
        assertThatThrownBy(() -> properties(request("005930"), Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsUnsupportedAllowedMarketOrSecurityTypeThroughExistingRequest() {
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true, List.of("005930"), DATE, CUTOFF,
                Set.of(StockMarket.OTHER), Set.of(StockSecurityType.COMMON_STOCK), DATES,
                TradingVenueScope.INTEGRATED, 100L, 2, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("eligibleMarkets must contain only KOSPI or KOSDAQ.");
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true, List.of("005930"), DATE, CUTOFF,
                Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.ETF), DATES,
                TradingVenueScope.INTEGRATED, 100L, 2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eligibleSecurityTypes must contain only supported individual stock types.");
    }

    @Test
    void rejectsDuplicateTargetsFutureTradingDateAndCutoffBeforeSelectionDate() {
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true, List.of("005930", "005930"), DATE,
                CUTOFF, Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK), DATES,
                TradingVenueScope.INTEGRATED, 100L, 2, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate target symbol: 005930");
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true, List.of("005930"), DATE,
                CUTOFF, Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK), List.of(DATE, DATE.plusDays(1)),
                TradingVenueScope.INTEGRATED, 100L, 2, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("tradingDates must not be after selectionAsOfDate.");
        assertThatThrownBy(() -> new StockCandidateEvaluationProperties(true, List.of("005930"), DATE,
                CUTOFF.minusSeconds(86_400), Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK), DATES,
                TradingVenueScope.INTEGRATED, 100L, 2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selectionCutoffAt must not be before selectionAsOfDate in Asia/Seoul.");
    }
}
