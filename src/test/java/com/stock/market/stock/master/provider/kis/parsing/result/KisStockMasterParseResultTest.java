package com.stock.market.stock.master.provider.kis.parsing.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterParseResultTest {
    @Test
    void defensivelyCopiesRecords() {
        var original = result();
        var records = new ArrayList<>(original.records());
        var copy = new KisStockMasterParseResult(original.market(), original.inputSha256(),
                original.parserVersion(), original.layoutVersion(), records);
        records.clear();

        assertThat(copy.records()).hasSize(2);
        assertThatThrownBy(() -> copy.records().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsCautionPresenceThatDoesNotMatchTheMarketLayout() {
        var kospi = new KisStockMasterParser().parse(KisStockMasterMarket.KOSPI, content(row(KisStockMasterMarket.KOSPI)));
        var kosdaq = new KisStockMasterParser().parse(KisStockMasterMarket.KOSDAQ, content(row(KisStockMasterMarket.KOSDAQ)));

        assertThatThrownBy(() -> new KisStockMasterParseResult(KisStockMasterMarket.KOSDAQ, kospi.inputSha256(),
                kospi.parserVersion(), kospi.layoutVersion(), kospi.records()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field presence");
        assertThatThrownBy(() -> new KisStockMasterParseResult(KisStockMasterMarket.KOSPI, kosdaq.inputSha256(),
                kosdaq.parserVersion(), kosdaq.layoutVersion(), kosdaq.records()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field presence");
    }

    @Test
    void preservesMarketSpecificCautionPresenceThroughJsonRoundTrip() throws Exception {
        var json = new ObjectMapper();
        for (var market : KisStockMasterMarket.values()) {
            var original = new KisStockMasterParser().parse(market, content(row(market)));

            assertThat(json.readValue(json.writeValueAsBytes(original), KisStockMasterParseResult.class)).isEqualTo(original);
        }
    }

    @Test
    void rejectsEmptyNullAndNonconsecutiveRecords() {
        var original = result();
        assertThatThrownBy(() -> new KisStockMasterParseResult(original.market(), original.inputSha256(),
                original.parserVersion(), original.layoutVersion(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisStockMasterParseResult(original.market(), original.inputSha256(),
                original.parserVersion(), original.layoutVersion(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockMasterParseResult(original.market(), original.inputSha256(),
                original.parserVersion(), original.layoutVersion(), List.of(original.records().getLast())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisStockMasterParseResult(original.market(), original.inputSha256(),
                original.parserVersion(), original.layoutVersion(), List.of(original.records().getLast(), original.records().getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingMarketBadHashAndMissingVersions() {
        var original = result();
        assertThatThrownBy(() -> new KisStockMasterParseResult(null, original.inputSha256(),
                original.parserVersion(), original.layoutVersion(), original.records())).isInstanceOf(NullPointerException.class);
        for (String hash : new String[]{null, "bad", "A".repeat(64)}) {
            assertThatThrownBy(() -> new KisStockMasterParseResult(original.market(), hash,
                    original.parserVersion(), original.layoutVersion(), original.records())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new KisStockMasterParseResult(original.market(), original.inputSha256(),
                " ", original.layoutVersion(), original.records())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisStockMasterParseResult(original.market(), original.inputSha256(),
                original.parserVersion(), null, original.records())).isInstanceOf(IllegalArgumentException.class);
    }

    private static KisStockMasterParseResult result() {
        var market = KisStockMasterMarket.KOSPI;
        return new KisStockMasterParser().parse(market,
                content(row(market), row(market, "005935", "KR7005931001", "SECOND")));
    }
}
