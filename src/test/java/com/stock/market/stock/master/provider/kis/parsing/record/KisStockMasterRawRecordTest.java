package com.stock.market.stock.master.provider.kis.parsing.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import org.junit.jupiter.api.Test;

import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterRawRecordTest {
    @Test
    void preservesBlankAndUnknownFixedWidthFieldsWithoutClassification() {
        var source = record();
        var record = new KisStockMasterRawRecord(1, source.symbol(), source.standardCode(), source.name(),
                "ZZ", " ", "9", "NOTADATE", "?", "U", " ", "y", "?", "        ", source.rawLine());

        assertThat(record.rawGroup()).isEqualTo("ZZ");
        assertThat(record.rawEtp()).isEqualTo(" ");
        assertThat(record.rawPreferred()).isEqualTo("9");
        assertThat(record.rawListingDate()).isEqualTo("NOTADATE");
        assertThat(record.rawBaseDate()).isEqualTo("        ");
        assertThat(record.rawSpac()).isEqualTo(" ");
        assertThat(record.rawManagement()).isEqualTo("y");
        assertThat(record.rawInvestmentCaution()).isEqualTo("?");
    }

    @Test
    void rejectsMissingOrWrongWidthRequiredRestrictionFields() {
        var source = record();
        for (String value : new String[]{null, "", "NN"}) {
            assertThatThrownBy(() -> copyRestrictions(source, value, "N", null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rawSpac");
            assertThatThrownBy(() -> copyRestrictions(source, "N", value, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rawManagement");
        }
        for (String value : new String[]{"", "NN"}) {
            assertThatThrownBy(() -> copyRestrictions(source, "N", "N", value))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rawInvestmentCaution");
        }
    }

    @Test
    void preservesUnprovidedAndBlankCautionAsDistinctJsonValues() throws Exception {
        var json = new ObjectMapper();
        var unprovided = copyRestrictions(record(), "Y", "N", null);
        var blank = copyRestrictions(record(), "Y", "N", " ");

        assertThat(unprovided.rawInvestmentCaution()).isNull();
        assertThat(blank.rawInvestmentCaution()).isEqualTo(" ");
        assertThat(unprovided).isNotEqualTo(blank);
        assertThat(json.valueToTree(unprovided).get("rawInvestmentCaution").isNull()).isTrue();
        assertThat(json.valueToTree(blank).get("rawInvestmentCaution").textValue()).isEqualTo(" ");
        assertThat(json.readValue(json.writeValueAsBytes(unprovided), KisStockMasterRawRecord.class)).isEqualTo(unprovided);
        assertThat(json.readValue(json.writeValueAsBytes(blank), KisStockMasterRawRecord.class)).isEqualTo(blank);
    }

    @Test
    void rejectsMissingIdentifiersAndInvalidLineNumber() {
        var source = record();
        assertThatThrownBy(() -> copy(source, 0, source.symbol(), source.standardCode(), source.rawEtp(), source.rawLine()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> copy(source, 1, " ", source.standardCode(), source.rawEtp(), source.rawLine()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> copy(source, 1, source.symbol(), null, source.rawEtp(), source.rawLine()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTrimmedRawFieldsAndMissingOrMultilinePayload() {
        var source = record();
        for (String etp : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> copy(source, 1, source.symbol(), source.standardCode(), etp, source.rawLine()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String rawLine : new String[]{null, "", source.rawLine() + "\n", source.rawLine() + "\r"}) {
            assertThatThrownBy(() -> copy(source, 1, source.symbol(), source.standardCode(), source.rawEtp(), rawLine))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static KisStockMasterRawRecord record() {
        return new KisStockMasterParser().parse(KisStockMasterMarket.KOSPI, content(row(KisStockMasterMarket.KOSPI)))
                .records().getFirst();
    }

    private static KisStockMasterRawRecord copy(
            KisStockMasterRawRecord source, int line, String symbol, String standardCode, String etp, String rawLine
    ) {
        return new KisStockMasterRawRecord(line, symbol, standardCode, source.name(), source.rawGroup(), etp,
                source.rawPreferred(), source.rawListingDate(), source.rawSuspension(), source.rawLiquidation(),
                source.rawSpac(), source.rawManagement(), source.rawInvestmentCaution(),
                source.rawBaseDate(), rawLine);
    }

    private static KisStockMasterRawRecord copyRestrictions(
            KisStockMasterRawRecord source, String spac, String management, String caution
    ) {
        return new KisStockMasterRawRecord(source.lineNumber(), source.symbol(), source.standardCode(), source.name(),
                source.rawGroup(), source.rawEtp(), source.rawPreferred(), source.rawListingDate(),
                source.rawSuspension(), source.rawLiquidation(), spac, management, caution,
                source.rawBaseDate(), source.rawLine());
    }
}
