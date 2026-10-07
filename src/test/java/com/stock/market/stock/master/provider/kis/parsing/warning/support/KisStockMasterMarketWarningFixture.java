package com.stock.market.stock.master.provider.kis.parsing.warning.support;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;

import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.put;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;

public final class KisStockMasterMarketWarningFixture {
    private KisStockMasterMarketWarningFixture() {
    }

    public static KisStockMasterParseResult source(KisStockMasterMarket market, String code, String preannouncement) {
        return new KisStockMasterParser().parse(market, content(warningRow(market, "005930", "KR7005930003", code, preannouncement)));
    }

    public static KisStockMasterParseResult multiRowSource(KisStockMasterMarket market) {
        return new KisStockMasterParser().parse(market, content(
                warningRow(market, "005930", "KR7005930003", "00", "N"),
                warningRow(market, "0001A0", "KR70001A0001", "03", "Y"),
                warningRow(market, "Q520100", "KRG520001006", " ?", "y")));
    }

    private static byte[] warningRow(KisStockMasterMarket market, String symbol, String standardCode, String code, String preannouncement) {
        // The Korean name makes character-based offsets incorrect even when the byte width is valid.
        byte[] input = row(market, symbol, standardCode, "\uc0bc\uc131\uc804\uc790");
        boolean kospi = market == KisStockMasterMarket.KOSPI;
        put(input, kospi ? 123 : 118, "?");
        put(input, kospi ? 124 : 119, code);
        put(input, kospi ? 126 : 121, preannouncement);
        put(input, kospi ? 127 : 122, "Y");
        return input;
    }
}
