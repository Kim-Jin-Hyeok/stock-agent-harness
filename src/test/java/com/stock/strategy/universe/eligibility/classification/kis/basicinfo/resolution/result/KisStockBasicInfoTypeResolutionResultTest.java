package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.KisStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result.KisStockBasicInfoTypeClassificationResult;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationResult;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.commonMatch;
import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.matching;
import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.policy;
import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture.sample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoTypeResolutionResultTest {
    @ParameterizedTest
    @EnumSource(KisStockBasicInfoTypeResolutionReasonCode.class)
    void rejectsEveryReferenceTypeAndReasonCombinationExceptTheConsistentOne(KisStockBasicInfoTypeResolutionReasonCode expectedReason) {
        var valid = sample(expectedReason);
        var types = Stream.concat(Stream.of((StockSecurityType) null), Stream.of(StockSecurityType.values())).toList();
        for (var reason : KisStockBasicInfoTypeResolutionReasonCode.values()) {
            for (var type : types) {
                if (reason == expectedReason && type == valid.referenceSecurityType()) {
                    assertThat(copy(valid, valid.matchingResult(), valid.masterClassification(), valid.basicInfoClassification(), type, reason))
                            .isEqualTo(valid);
                } else {
                    assertThatThrownBy(() -> copy(valid, valid.matchingResult(), valid.masterClassification(), valid.basicInfoClassification(), type, reason))
                            .isInstanceOf(IllegalArgumentException.class);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockBasicInfoTypeResolutionReasonCode.class)
    void retainsCompleteInputsAndMetadataAcrossJsonRoundTrip(KisStockBasicInfoTypeResolutionReasonCode reason) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var result = sample(reason);
        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoTypeResolutionResult.class);
        assertThat(restored).isEqualTo(result);
        JsonNode restoredJson = mapper.valueToTree(restored);
        assertThat(restoredJson).isEqualTo(mapper.valueToTree(result));
        assertThat(restored.matchingResult().masterBatch()).isEqualTo(result.matchingResult().masterBatch());
        assertThat(restored.matchingResult().apiInput()).isEqualTo(result.matchingResult().apiInput());
    }

    @Test
    void retainsMissingRequestAndNullClassificationsAcrossJsonRoundTrip() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var result = policy().resolve(matching("999999", "KR7005930003", "STK", "ST", "101"));
        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoTypeResolutionResult.class);
        assertThat(restored).isEqualTo(result);
        assertThat(restored.matchingResult().requestedSymbol()).isEqualTo("999999");
        assertThat(restored.masterClassification()).isNull();
        assertThat(restored.basicInfoClassification()).isNull();
        assertThat(restored.referenceSecurityType()).isNull();
    }

    @Test
    void rejectsAbsentMasterClassificationForAnExistingRequestedRecord() {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        assertThatThrownBy(() -> copy(result, result.matchingResult(), null, result.basicInfoClassification(), result.referenceSecurityType(), result.reasonCode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectedMasterForAMissingRequestedRecord() {
        var missing = policy().resolve(matching("999999", "KR7005930003", "STK", "ST", "101"));
        var existing = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        assertThat(missing.masterClassification()).isNull();
        assertThatThrownBy(() -> copy(missing, missing.matchingResult(), existing.masterClassification(), null, null, missing.reasonCode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOtherMasterRowsEvenFromTheSameBatch() {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        var other = sample(KisStockBasicInfoTypeResolutionReasonCode.MASTER_TYPE_ONLY);
        assertThat(result.matchingResult().masterBatch()).isEqualTo(other.matchingResult().masterBatch());
        assertThatThrownBy(() -> copy(result, result.matchingResult(), other.masterClassification(), result.basicInfoClassification(),
                result.referenceSecurityType(), result.reasonCode())).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NAME", "MARKET"})
    void rejectsAlteredMasterClassificationsDespiteIdenticalIdentifiers(String changedField) {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        var master = result.masterClassification();
        var raw = master.rawRecord();
        var changedRaw = new KisStockMasterRawRecord(
                raw.lineNumber(), raw.symbol(), raw.standardCode(), "ALTERED NAME", raw.rawGroup(), raw.rawEtp(),
                raw.rawPreferred(), raw.rawListingDate(), raw.rawSuspension(), raw.rawLiquidation(), raw.rawSpac(),
                raw.rawManagement(), raw.rawInvestmentCaution(), raw.rawBaseDate(), raw.rawLine());
        var changed = new KisStockMasterTypeClassificationResult(changedField.equals("MARKET")
                ? KisStockMasterMarket.KOSDAQ : master.market(),
                changedField.equals("NAME") ? changedRaw : raw, master.securityType(), master.reasonCode(),
                master.classificationVersion(), master.sourceRevision());
        assertThatThrownBy(() -> copy(result, result.matchingResult(), changed, result.basicInfoClassification(), result.referenceSecurityType(), result.reasonCode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRemovedApiClassificationForASuccessfulMatch() {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.MASTER_TYPE_ONLY);
        assertThatThrownBy(() -> copy(result, result.matchingResult(), result.masterClassification(), null, result.referenceSecurityType(), result.reasonCode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInjectedApiClassificationForAFailedMatch() {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.MATCH_NOT_CONFIRMED);
        var classification = new KisStockBasicInfoTypeClassificationPolicy().classify(result.matchingResult().apiInput());
        assertThatThrownBy(() -> copy(result, result.matchingResult(), result.masterClassification(), classification, null, result.reasonCode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsApiClassificationForAnotherCompleteParseInput() {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        var other = policy().resolve(matching("0004Y0", "KR70004Y0000", "KSQ", "ST", "101"));
        assertThatThrownBy(() -> copy(result, result.matchingResult(), result.masterClassification(), other.basicInfoClassification(),
                result.referenceSecurityType(), result.reasonCode())).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HASH", "PARSER_VERSION", "MESSAGE"})
    void rejectsChangedApiProvenanceEvenWhenRawRecordIsIdentical(String field) {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        var classification = result.basicInfoClassification();
        var parsed = classification.parseResult();
        var changedParse = new KisStockBasicInfoParseResult(field.equals("HASH") ? "0".repeat(64) : parsed.inputSha256(),
                field.equals("PARSER_VERSION") ? "OTHER_PARSER" : parsed.parserVersion(), parsed.responseCode(), parsed.messageCode(),
                field.equals("MESSAGE") ? "ALTERED MESSAGE" : parsed.message(), parsed.rawRecord());
        var changed = new KisStockBasicInfoTypeClassificationResult(changedParse, classification.securityType(), classification.reasonCode(),
                classification.classificationVersion(), classification.sourceReference(), classification.sourceSha256());
        assertThatThrownBy(() -> copy(result, result.matchingResult(), result.masterClassification(), changed,
                result.referenceSecurityType(), result.reasonCode())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSuccessfulResolutionWhenItsMatchingInputIsReplacedWithAFailure() {
        var result = policy().resolve(commonMatch());
        var failedMatch = matching("005930", "KR7005930003", "KSQ", "ST", "101");
        assertThatThrownBy(() -> copy(result, failedMatch, result.masterClassification(), result.basicInfoClassification(),
                result.referenceSecurityType(), result.reasonCode())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullMatchOrReason() {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        assertThatThrownBy(() -> copy(result, null, result.masterClassification(), result.basicInfoClassification(), result.referenceSecurityType(), result.reasonCode()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> copy(result, result.matchingResult(), result.masterClassification(), result.basicInfoClassification(), result.referenceSecurityType(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankResolutionVersion(String version) {
        var result = sample(KisStockBasicInfoTypeResolutionReasonCode.BASIC_INFO_TYPE_SUPPLEMENTED);
        assertThatThrownBy(() -> new KisStockBasicInfoTypeResolutionResult(version, result.matchingResult(), result.masterClassification(),
                result.basicInfoClassification(), result.referenceSecurityType(), result.reasonCode())).isInstanceOf(IllegalArgumentException.class);
    }

    private KisStockBasicInfoTypeResolutionResult copy(KisStockBasicInfoTypeResolutionResult result,
            KisStockBasicInfoMatchResult matching, KisStockMasterTypeClassificationResult master,
            KisStockBasicInfoTypeClassificationResult basicInfo, StockSecurityType type, KisStockBasicInfoTypeResolutionReasonCode reason) {
        return new KisStockBasicInfoTypeResolutionResult(result.resolutionVersion(), matching, master, basicInfo, type, reason);
    }
}
