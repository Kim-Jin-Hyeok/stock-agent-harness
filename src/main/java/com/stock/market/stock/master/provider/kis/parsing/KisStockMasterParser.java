package com.stock.market.stock.master.provider.kis.parsing;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.record.KisStockMasterRawRecord;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

public class KisStockMasterParser {
    public static final String PARSER_VERSION = "KIS_STOCK_MASTER_RAW_V2";
    public static final String LAYOUT_VERSION = "OBSERVED_2026_10_05_LF_V1";
    public static final int MAX_CONTENT_BYTES = 32 * 1024 * 1024;
    private static final Charset CP949 = Charset.forName("MS949");
    private static final int PREFIX_BYTES = 61;
    // Byte offsets from the retained observation, not a provider-wide schema version.
    private static final Layout KOSPI = new Layout(288, 83, 219, 166, 121, 122, 90, 123, null, 265);
    private static final Layout KOSDAQ = new Layout(282, 79, 214, 161, 116, 117, 85, 118, 91, 259);

    public KisStockMasterParseResult parse(KisStockMasterMarket market, byte[] content) {
        Objects.requireNonNull(market, "market must not be null.");
        Objects.requireNonNull(content, "content must not be null.");
        if (content.length == 0 || content.length > MAX_CONTENT_BYTES) {
            throw failure(market, 1, "Content must be nonempty and at most 32 MiB.");
        }
        byte[] input = content.clone();
        Layout layout = switch (market) {
            case KOSPI -> KOSPI;
            case KOSDAQ -> KOSDAQ;
        };
        var records = new ArrayList<KisStockMasterRawRecord>();
        var symbols = new HashMap<String, Integer>();
        var standardCodes = new HashMap<String, Integer>();
        int start = 0;
        int lineNumber = 1;
        for (int index = 0; index < input.length; index++) {
            if (input[index] == '\r') {
                throw failure(market, lineNumber, "CR is not supported by the observed LF layout.");
            }
            if (input[index] != '\n') {
                if (index - start >= layout.rowBytes()) {
                    throw failure(market, lineNumber, "Payload exceeds expected bytes=" + layout.rowBytes() + ".");
                }
                continue;
            }
            int length = index - start;
            if (length != layout.rowBytes()) {
                throw failure(market, lineNumber, "Unexpected payload bytes=" + length
                        + ", expected=" + layout.rowBytes() + ".");
            }
            var record = parseRow(market, lineNumber, input, start, layout);
            requireUnique(symbols, record.symbol(), "symbol", market, lineNumber);
            requireUnique(standardCodes, record.standardCode(), "standardCode", market, lineNumber);
            records.add(record);
            start = index + 1;
            lineNumber++;
        }
        if (start != input.length) {
            throw failure(market, lineNumber, "Final record must have an LF terminator.");
        }
        return new KisStockMasterParseResult(market, sha256(input), PARSER_VERSION, LAYOUT_VERSION, records);
    }

    private KisStockMasterRawRecord parseRow(
            KisStockMasterMarket market, int lineNumber, byte[] input, int start, Layout layout
    ) {
        String rawLine = decode(input, start, layout.rowBytes(), market, lineNumber);
        requireRoundTrip(input, start, layout.rowBytes(), rawLine, market, lineNumber);
        String symbol = trimPadding(ascii(input, start, 9, market, lineNumber));
        String standardCode = trimPadding(ascii(input, start + 9, 12, market, lineNumber));
        if (symbol.isBlank() || standardCode.isBlank()) {
            throw failure(market, lineNumber, "Stock master identifiers must not be blank.");
        }
        String name = decode(input, start + 21, 40, market, lineNumber);
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw failure(market, lineNumber, "Name must not contain control characters.");
        }
        String tail = ascii(input, start + PREFIX_BYTES, layout.rowBytes() - PREFIX_BYTES, market, lineNumber);
        return new KisStockMasterRawRecord(lineNumber, symbol, standardCode, trimPadding(name),
                tail.substring(0, 2), field(tail, layout.etpOffset(), 1),
                field(tail, layout.preferredOffset(), 1), field(tail, layout.listingDateOffset(), 8),
                field(tail, layout.suspensionOffset(), 1), field(tail, layout.liquidationOffset(), 1),
                field(tail, layout.spacOffset(), 1), field(tail, layout.managementOffset(), 1),
                layout.investmentCautionOffset() == null ? null : field(tail, layout.investmentCautionOffset(), 1),
                field(tail, layout.baseDateOffset(), 8), rawLine);
    }

    private static String field(String tail, int absoluteOffset, int width) {
        return tail.substring(absoluteOffset - PREFIX_BYTES, absoluteOffset - PREFIX_BYTES + width);
    }

    private static String ascii(byte[] input, int start, int length, KisStockMasterMarket market, int line) {
        for (int index = start; index < start + length; index++) {
            int value = Byte.toUnsignedInt(input[index]);
            if (value < 32 || value > 126) {
                throw failure(market, line, "Identifier or tail field must contain printable ASCII bytes.");
            }
        }
        return new String(input, start, length, StandardCharsets.US_ASCII);
    }

    private static String decode(byte[] input, int start, int length, KisStockMasterMarket market, int line) {
        try {
            return CP949.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(input, start, length)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(context(market, line) + "Invalid CP949 bytes.", exception);
        }
    }

    private static void requireRoundTrip(
            byte[] input, int start, int length, String rawLine, KisStockMasterMarket market, int line
    ) {
        try {
            ByteBuffer encoded = CP949.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(rawLine));
            byte[] restored = new byte[encoded.remaining()];
            encoded.get(restored);
            if (!Arrays.equals(input, start, start + length, restored, 0, restored.length)) {
                throw failure(market, line, "CP949 round trip does not preserve the input bytes.");
            }
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(context(market, line) + "CP949 re-encoding failed.", exception);
        }
    }

    private static String trimPadding(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    private static void requireUnique(
            Map<String, Integer> firstLines, String value, String field, KisStockMasterMarket market, int line
    ) {
        Integer firstLine = firstLines.putIfAbsent(value, line);
        if (firstLine != null) {
            throw failure(market, line, "Duplicate " + field + ". firstLine=" + firstLine + ".");
        }
    }

    private static IllegalArgumentException failure(KisStockMasterMarket market, int line, String reason) {
        return new IllegalArgumentException(context(market, line) + reason);
    }

    private static String context(KisStockMasterMarket market, int line) {
        return "Stock master parsing failed. market=" + market + ", line=" + line + ", cause=";
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }

    private record Layout(
            int rowBytes, int etpOffset, int preferredOffset, int listingDateOffset,
            int suspensionOffset, int liquidationOffset, int spacOffset, int managementOffset,
            Integer investmentCautionOffset, int baseDateOffset
    ) {
    }
}
