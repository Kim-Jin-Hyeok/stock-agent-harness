package com.stock.market.stock.master.provider.kis.parsing.record;

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
                "ZZ", " ", "9", "NOTADATE", "?", "U", "        ", source.rawLine());

        assertThat(record.rawGroup()).isEqualTo("ZZ");
        assertThat(record.rawEtp()).isEqualTo(" ");
        assertThat(record.rawPreferred()).isEqualTo("9");
        assertThat(record.rawListingDate()).isEqualTo("NOTADATE");
        assertThat(record.rawBaseDate()).isEqualTo("        ");
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
                source.rawBaseDate(), rawLine);
    }
}
