package com.stock.strategy.universe.eligibility.restriction.kis.freshness.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_BASIC_INFO_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_MASTER_AGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionFreshnessRequestTest {
    @Test
    void preservesExplicitEvaluationTimeAndIndependentPositiveAges() {
        var request = new KisStockRestrictionFreshnessRequest(EVALUATED_AT, MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);

        assertThat(request.evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(request.maxMasterAge()).isEqualTo(MAX_MASTER_AGE);
        assertThat(request.maxBasicInfoAge()).isEqualTo(MAX_BASIC_INFO_AGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"evaluatedAt", "maxMasterAge", "maxBasicInfoAge"})
    void rejectsMissingEvaluationTimeOrAgeWithoutDefaults(String missing) {
        assertThatThrownBy(() -> new KisStockRestrictionFreshnessRequest(
                missing.equals("evaluatedAt") ? null : EVALUATED_AT,
                missing.equals("maxMasterAge") ? null : MAX_MASTER_AGE,
                missing.equals("maxBasicInfoAge") ? null : MAX_BASIC_INFO_AGE))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
    }

    @ParameterizedTest
    @CsvSource({"maxMasterAge, PT0S", "maxMasterAge, PT-1H", "maxMasterAge, PT-0.000000001S",
            "maxBasicInfoAge, PT0S", "maxBasicInfoAge, PT-1H", "maxBasicInfoAge, PT-0.000000001S"})
    void rejectsZeroAndNegativeAges(String field, Duration invalid) {
        assertThatThrownBy(() -> new KisStockRestrictionFreshnessRequest(EVALUATED_AT,
                field.equals("maxMasterAge") ? invalid : MAX_MASTER_AGE,
                field.equals("maxBasicInfoAge") ? invalid : MAX_BASIC_INFO_AGE))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(field + " must be positive.");
    }

    @Test
    void acceptsNanosecondAndLargePositiveDurationsWithoutAddingThemToAnInstant() {
        var request = new KisStockRestrictionFreshnessRequest(Instant.MAX, Duration.ofNanos(1), Duration.ofSeconds(Long.MAX_VALUE));

        assertThat(request.evaluatedAt()).isEqualTo(Instant.MAX);
        assertThat(request.maxMasterAge()).isEqualTo(Duration.ofNanos(1));
        assertThat(request.maxBasicInfoAge()).isEqualTo(Duration.ofSeconds(Long.MAX_VALUE));
    }

    @Test
    void roundTripsNanosecondTimeAndDistinctAgesAsJson() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var request = new KisStockRestrictionFreshnessRequest(EVALUATED_AT, Duration.ofSeconds(7, 11), Duration.ofNanos(19));

        assertThat(mapper.readValue(mapper.writeValueAsBytes(request), KisStockRestrictionFreshnessRequest.class)).isEqualTo(request);
    }
}
