package com.stock.market.stock.master.provider.kis.parsing.support;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class KisStockMasterParsingFixture {
    public static final Charset CP949 = Charset.forName("MS949");
    public static final String NAME = "\uc0bc\uc131\uc804\uc790";

    private KisStockMasterParsingFixture() {
    }

    public static byte[] row(KisStockMasterMarket market) {
        return row(market, "005930", "KR7005930003", NAME);
    }

    public static byte[] row(KisStockMasterMarket market, String symbol, String standardCode, String name) {
        byte[] row = new byte[market == KisStockMasterMarket.KOSPI ? 288 : 282];
        Arrays.fill(row, (byte) ' ');
        Arrays.fill(row, 61, row.length, (byte) '~');
        write(row, 0, 9, symbol.getBytes(StandardCharsets.US_ASCII));
        write(row, 9, 12, standardCode.getBytes(StandardCharsets.US_ASCII));
        write(row, 21, 40, name.getBytes(CP949));
        put(row, 61, "ST");
        if (market == KisStockMasterMarket.KOSPI) {
            put(row, 83, " ");
            put(row, 219, "0");
            put(row, 166, "19750611");
            put(row, 121, "N");
            put(row, 122, "N");
            put(row, 265, "20260630");
        } else {
            put(row, 79, " ");
            put(row, 214, "0");
            put(row, 161, "19750611");
            put(row, 116, "N");
            put(row, 117, "N");
            put(row, 259, "20260630");
        }
        return row;
    }

    public static byte[] content(byte[]... rows) {
        var output = new ByteArrayOutputStream();
        for (byte[] row : rows) {
            output.writeBytes(row);
            output.write('\n');
        }
        return output.toByteArray();
    }

    public static void put(byte[] row, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, row, offset, bytes.length);
    }

    private static void write(byte[] row, int offset, int width, byte[] value) {
        if (value.length > width) {
            throw new IllegalArgumentException("Fixture field exceeds its byte width.");
        }
        System.arraycopy(value, 0, row, offset, value.length);
    }
}
