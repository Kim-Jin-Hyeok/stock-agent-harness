package com.stock.strategy.universe.eligibility.classification.kis;

import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.strategy.universe.eligibility.classification.kis.result.KisStockMasterTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture.collect;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.put;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterTypeClassificationPolicyTest {
    private final KisStockMasterTypeClassificationPolicy policy = new KisStockMasterTypeClassificationPolicy();
    private final KisStockMasterParser parser = new KisStockMasterParser();

    @Test
    void interpretsOnlyReviewedKospiEtfCombinationWithFixedRuleMetadata() {
        var raw = record(KisStockMasterMarket.KOSPI, "EF", "2", "0");

        var result = policy.classify(KisStockMasterMarket.KOSPI, raw);

        assertThat(result.market()).isEqualTo(KisStockMasterMarket.KOSPI);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.reasonCode()).isEqualTo(KisStockMasterTypeClassificationReasonCode.TYPE_INTERPRETED);
        assertThat(result.classificationVersion()).isEqualTo("KIS_STOCK_MASTER_CURRENT_TYPE_V1");
        assertThat(result.sourceRevision()).isEqualTo("277ec0eb7a9b7f63b6807829286c80f36649dad2");
    }

    @Test
    void doesNotExtendReviewedCombinationToKosdaq() {
        assertReason(KisStockMasterMarket.KOSDAQ, "EF", "2", "0",
                KisStockMasterTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void preservesBlankEtpInsteadOfTreatingItAsZero(KisStockMasterMarket market) {
        var raw = record(market, "ST", " ", "0");

        var result = policy.classify(market, raw);

        assertThat(result.securityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.rawRecord().rawEtp()).isEqualTo(" ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN", "PF", "ef", "  ", "XX"})
    void doesNotInferUndefinedGroupsFromOtherFieldsOrNames(String group) {
        assertReason(KisStockMasterMarket.KOSPI, group, "2", "0",
                KisStockMasterTypeClassificationReasonCode.GROUP_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @CsvSource({"KOSPI,8", "KOSPI,9", "KOSPI,X", "KOSDAQ,5", "KOSDAQ,8", "KOSDAQ,9", "KOSDAQ,X"})
    void rejectsEtpValuesOutsideTheMarketSpecificDefinition(KisStockMasterMarket market, String etp) {
        assertReason(market, "EF", etp, "0", KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9", " ", "X"})
    void keepsUndefinedPreferredValuesUnverified(String preferred) {
        assertReason(KisStockMasterMarket.KOSPI, "EF", "2", preferred,
                KisStockMasterTypeClassificationReasonCode.PREFERRED_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ST", "MF", "RT", "SC", "IF", "DR", "EW", "SW", "SR", "BC", "FE", "FS"})
    void doesNotClassifyOtherDefinedGroupsByDefault(String group) {
        assertReason(KisStockMasterMarket.KOSPI, group, "0", "0",
                KisStockMasterTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
    }

    @ParameterizedTest
    @CsvSource({"ST,0,1", "ST,0,2", "FE,2,0", "EF,3,0", "EF,4,0", "EF,2,1", "EF,2,2", "BC,5,0"})
    void keepsDefinedButUnsupportedCombinationsUnverified(String group, String etp, String preferred) {
        assertReason(KisStockMasterMarket.KOSPI, group, etp, preferred,
                KisStockMasterTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
    }

    @Test
    void reportsFirstUnverifiedFieldWithoutDiscardingOtherRawValues() {
        assertReason(KisStockMasterMarket.KOSPI, "EN", "8", "9",
                KisStockMasterTypeClassificationReasonCode.GROUP_VALUE_UNVERIFIED);
        assertReason(KisStockMasterMarket.KOSPI, "EF", "8", "9",
                KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
        assertReason(KisStockMasterMarket.KOSPI, "EF", "2", "9",
                KisStockMasterTypeClassificationReasonCode.PREFERRED_VALUE_UNVERIFIED);
        assertReason(KisStockMasterMarket.KOSDAQ, "ST", " ", "9",
                KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void preservesAlphanumericIdentifiersAndOriginalLine(KisStockMasterMarket market) {
        byte[] bytes = row(market, "0001A0", "KR70001A0001", "ALPHA");
        var raw = parser.parse(market, content(bytes)).records().getFirst();

        var result = policy.classify(market, raw);

        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.rawRecord().symbol()).isEqualTo("0001A0");
        assertThat(result.rawRecord().standardCode()).isEqualTo("KR70001A0001");
        assertThat(result.rawRecord().rawLine()).isEqualTo(raw.rawLine());
        assertThat(result.securityType()).isNull();
    }

    @Test
    void doesNotInterpretDatesOrTradingFlagsAsListingOrOrderEligibility() {
        byte[] bytes = classifiedRow(KisStockMasterMarket.KOSPI, "EF", "2", "0");
        put(bytes, 166, "NOTADATE");
        put(bytes, 265, "        ");
        put(bytes, 121, "Y");
        put(bytes, 122, "Y");
        var raw = parser.parse(KisStockMasterMarket.KOSPI, content(bytes)).records().getFirst();

        var result = policy.classify(KisStockMasterMarket.KOSPI, raw);

        assertThat(result.securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.rawRecord().rawListingDate()).isEqualTo("NOTADATE");
        assertThat(result.rawRecord().rawBaseDate()).isEqualTo("        ");
        assertThat(result.rawRecord().rawSuspension()).isEqualTo("Y");
        assertThat(result.rawRecord().rawLiquidation()).isEqualTo("Y");
    }

    @Test
    void classifiesParsedBatchRowsWithoutChangingTheirCollectionContract(@TempDir Path root) throws Exception {
        var collection = collect(root, content(classifiedRow(KisStockMasterMarket.KOSPI, "EF", "2", "0")),
                content(row(KisStockMasterMarket.KOSDAQ, "0001A0", "KR70001A0001", "ALPHA")));
        var batch = new StockMasterBatchParsingService(parser).parseBatch(root, collection.collectionId());

        var results = batch.marketResults().stream().flatMap(market -> market.records().stream()
                .map(raw -> policy.classify(market.market(), raw))).toList();

        assertThat(batch.collection()).isEqualTo(collection);
        assertThat(batch.collection().evidenceScope()).isEqualTo("CURRENT_OBSERVATION");
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(results.getLast().securityType()).isNull();
        assertThat(results.getLast().reasonCode()).isEqualTo(KisStockMasterTypeClassificationReasonCode.ETP_VALUE_UNVERIFIED);
        assertThat(results.getFirst().rawRecord()).isSameAs(batch.marketResults().getFirst().records().getFirst());
        assertThat(results.getLast().rawRecord()).isSameAs(batch.marketResults().getLast().records().getFirst());
    }

    @Test
    void doesNotRetainPreviousClassificationBetweenCalls() {
        var supported = record(KisStockMasterMarket.KOSPI, "EF", "2", "0");
        var unsupported = record(KisStockMasterMarket.KOSPI, "ST", " ", "0");
        var first = policy.classify(KisStockMasterMarket.KOSPI, supported);

        assertThat(policy.classify(KisStockMasterMarket.KOSPI, unsupported).securityType()).isNull();
        assertThat(policy.classify(KisStockMasterMarket.KOSPI, supported)).isEqualTo(first);
    }

    @Test
    void rejectsNullArguments() {
        var raw = record(KisStockMasterMarket.KOSPI, "EF", "2", "0");
        assertThatThrownBy(() -> policy.classify(null, raw)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.classify(KisStockMasterMarket.KOSPI, null)).isInstanceOf(NullPointerException.class);
    }

    private void assertReason(KisStockMasterMarket market, String group, String etp, String preferred,
                              KisStockMasterTypeClassificationReasonCode reason) {
        var raw = record(market, group, etp, preferred);
        var result = policy.classify(market, raw);
        assertThat(result.securityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.rawRecord()).isSameAs(raw);
        assertThat(result.rawRecord().rawGroup()).isEqualTo(group);
        assertThat(result.rawRecord().rawEtp()).isEqualTo(etp);
        assertThat(result.rawRecord().rawPreferred()).isEqualTo(preferred);
    }

    private KisStockMasterRawRecord record(KisStockMasterMarket market, String group, String etp, String preferred) {
        return parser.parse(market, content(classifiedRow(market, group, etp, preferred))).records().getFirst();
    }

    private static byte[] classifiedRow(KisStockMasterMarket market, String group, String etp, String preferred) {
        byte[] bytes = row(market);
        put(bytes, 61, group);
        put(bytes, market == KisStockMasterMarket.KOSPI ? 83 : 79, etp);
        put(bytes, market == KisStockMasterMarket.KOSPI ? 219 : 214, preferred);
        return bytes;
    }
}
