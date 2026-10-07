package com.stock.market.stock.master.provider.kis.parsing.warning;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;
import com.stock.market.stock.master.provider.kis.parsing.warning.record.KisStockMasterMarketWarningRawRecord;
import com.stock.market.stock.master.provider.kis.parsing.warning.result.KisStockMasterMarketWarningParseResult;

import java.io.ByteArrayOutputStream;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KisStockMasterMarketWarningParser {
    public static final String PARSER_VERSION = "KIS_STOCK_MASTER_MARKET_WARNING_RAW_V1";
    public static final String SOURCE_REVISION = "277ec0eb7a9b7f63b6807829286c80f36649dad2";
    private static final Charset CP949 = Charset.forName("MS949");

    public KisStockMasterMarketWarningParseResult parse(KisStockMasterParseResult source) {
        return new KisStockMasterMarketWarningParseResult(source, extractRecords(source), PARSER_VERSION, SOURCE_REVISION);
    }

    public static List<KisStockMasterMarketWarningRawRecord> extractRecords(KisStockMasterParseResult source) {
        Objects.requireNonNull(source, "source must not be null.");
        // These offsets belong only to the retained layout, not future parser or provider versions.
        if (!"KIS_STOCK_MASTER_RAW_V2".equals(source.parserVersion())
                || !"OBSERVED_2026_10_05_LF_V1".equals(source.layoutVersion())) {
            throw new IllegalArgumentException("Market warning extraction requires the verified master parser and layout versions.");
        }
        boolean kospi = source.market() == KisStockMasterMarket.KOSPI;
        int rowBytes = kospi ? 288 : 282;
        int warningOffset = kospi ? 124 : 119;
        int preannouncementOffset = kospi ? 126 : 121;
        byte[] content = reconstructContent(source, rowBytes);
        var reparsed = new KisStockMasterParser().parse(source.market(), content);
        if (!reparsed.equals(source)) {
            throw new IllegalArgumentException("Market warning source must agree with the complete raw lines, input hash and original parsed fields.");
        }
        var records = new ArrayList<KisStockMasterMarketWarningRawRecord>(source.records().size());
        for (int index = 0; index < source.records().size(); index++) {
            var original = source.records().get(index);
            int start = index * (rowBytes + 1);
            records.add(new KisStockMasterMarketWarningRawRecord(original.lineNumber(), original.symbol(), original.standardCode(),
                    new String(content, start + warningOffset, 2, StandardCharsets.US_ASCII),
                    new String(content, start + preannouncementOffset, 1, StandardCharsets.US_ASCII)));
        }
        return List.copyOf(records);
    }

    private static byte[] reconstructContent(KisStockMasterParseResult source, int rowBytes) {
        if (source.records().size() > KisStockMasterParser.MAX_CONTENT_BYTES / (rowBytes + 1)) {
            throw new IllegalArgumentException("Market warning source exceeds the master content size limit.");
        }
        var content = new ByteArrayOutputStream(source.records().size() * (rowBytes + 1));
        for (var record : source.records()) {
            if (record.rawLine().length() > rowBytes) {
                throw new IllegalArgumentException("Market warning source raw line exceeds the verified byte width.");
            }
            try {
                var encoded = CP949.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(record.rawLine()));
                if (encoded.remaining() != rowBytes) {
                    throw new IllegalArgumentException("Market warning source raw line must match the verified byte width.");
                }
                byte[] bytes = new byte[rowBytes];
                encoded.get(bytes);
                content.writeBytes(bytes);
                content.write('\n');
            } catch (CharacterCodingException exception) {
                throw new IllegalArgumentException("Market warning source raw line must be strictly encodable as CP949.");
            }
        }
        return content.toByteArray();
    }
}
