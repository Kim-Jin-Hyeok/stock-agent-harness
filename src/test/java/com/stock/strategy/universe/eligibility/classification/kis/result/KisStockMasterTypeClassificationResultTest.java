package com.stock.strategy.universe.eligibility.classification.kis.result;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.put;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterTypeClassificationResultTest {
    private final KisStockMasterRawRecord raw = supportedRecord();
    private final String version = KisStockMasterTypeClassificationPolicy.CLASSIFICATION_VERSION;
    private final String revision = KisStockMasterTypeClassificationPolicy.SOURCE_REVISION;

    @ParameterizedTest
    @EnumSource(value = KisStockMasterTypeClassificationReasonCode.class, names = "TYPE_INTERPRETED", mode = EnumSource.Mode.EXCLUDE)
    void keepsDeferredResultWithoutAType(KisStockMasterTypeClassificationReasonCode reason) {
        var result = result(null, reason);

        assertThat(result.securityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.classificationVersion()).isEqualTo(version);
        assertThat(result.sourceRevision()).isEqualTo(revision);
    }

    @Test
    void requiresATypeForInterpretedResult() {
        assertThat(result(StockSecurityType.ETF, KisStockMasterTypeClassificationReasonCode.TYPE_INTERPRETED).securityType())
                .isEqualTo(StockSecurityType.ETF);
        assertThatThrownBy(() -> result(null, KisStockMasterTypeClassificationReasonCode.TYPE_INTERPRETED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TYPE_INTERPRETED");
    }

    @ParameterizedTest
    @EnumSource(value = KisStockMasterTypeClassificationReasonCode.class, names = "TYPE_INTERPRETED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsDefaultTypeForDeferredReason(KisStockMasterTypeClassificationReasonCode reason) {
        for (var type : StockSecurityType.values()) {
            assertThatThrownBy(() -> result(type, reason)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsMissingMarketRecordOrReason() {
        assertThatThrownBy(() -> new KisStockMasterTypeClassificationResult(null, raw, null,
                KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED, version, revision))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockMasterTypeClassificationResult(KisStockMasterMarket.KOSPI, null, null,
                KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED, version, revision))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> result(null, null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingClassificationVersion(String value) {
        assertThatThrownBy(() -> new KisStockMasterTypeClassificationResult(KisStockMasterMarket.KOSPI, raw, null,
                KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED, value, revision))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classificationVersion");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"main", "277ec0e", "277EC0EB7A9B7F63B6807829286C80F36649DAD2", "g77ec0eb7a9b7f63b6807829286c80f36649dad2"})
    void rejectsUnpinnedOrMalformedSourceRevision(String value) {
        assertThatThrownBy(() -> new KisStockMasterTypeClassificationResult(KisStockMasterMarket.KOSPI, raw, null,
                KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED, version, value))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceRevision");
    }

    private KisStockMasterTypeClassificationResult result(StockSecurityType type, KisStockMasterTypeClassificationReasonCode reason) {
        return new KisStockMasterTypeClassificationResult(KisStockMasterMarket.KOSPI, raw, type, reason, version, revision);
    }

    private static KisStockMasterRawRecord supportedRecord() {
        byte[] bytes = row(KisStockMasterMarket.KOSPI);
        put(bytes, 61, "EF");
        put(bytes, 83, "2");
        return new KisStockMasterParser().parse(KisStockMasterMarket.KOSPI, content(bytes)).records().getFirst();
    }
}
