package com.stock.market.stock.master.provider.kis.parsing;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.CP949;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.NAME;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.put;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockMasterParserTest {
    private final KisStockMasterParser parser = new KisStockMasterParser();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void parsesByteLayoutAndRetainsRawLineHashAndVersions(KisStockMasterMarket market) throws Exception {
        byte[] row = row(market);
        byte[] input = content(row);
        byte[] before = input.clone();

        var result = parser.parse(market, input);
        var record = result.records().getFirst();

        assertThat(result.market()).isEqualTo(market);
        assertThat(result.inputSha256()).isEqualTo(sha256(input));
        assertThat(result.parserVersion()).isEqualTo(KisStockMasterParser.PARSER_VERSION);
        assertThat(result.parserVersion()).isEqualTo("KIS_STOCK_MASTER_RAW_V2");
        assertThat(result.layoutVersion()).isEqualTo(KisStockMasterParser.LAYOUT_VERSION);
        assertThat(result.records()).hasSize(1);
        assertThat(record.lineNumber()).isEqualTo(1);
        assertThat(record.symbol()).isEqualTo("005930");
        assertThat(record.standardCode()).isEqualTo("KR7005930003");
        assertThat(record.name()).isEqualTo(NAME);
        assertThat(record.rawGroup()).isEqualTo("ST");
        assertThat(record.rawEtp()).isEqualTo(" ");
        assertThat(record.rawPreferred()).isEqualTo("0");
        assertThat(record.rawListingDate()).isEqualTo("19750611");
        assertThat(record.rawSuspension()).isEqualTo("N");
        assertThat(record.rawLiquidation()).isEqualTo("N");
        assertThat(record.rawSpac()).isEqualTo("N");
        assertThat(record.rawManagement()).isEqualTo("N");
        assertThat(record.rawInvestmentCaution()).isEqualTo(market == KisStockMasterMarket.KOSPI ? null : "N");
        assertThat(record.rawBaseDate()).isEqualTo("20260630");
        assertThat(record.rawLine()).isEqualTo(new String(row, CP949));
        assertThat(record.rawLine().getBytes(CP949)).isEqualTo(row);
        assertThat(input).isEqualTo(before);
        assertThat(parser.parse(market, input)).isEqualTo(result);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void extractsRestrictionFieldsIndependentlyAtTheirByteOffsets(KisStockMasterMarket market) {
        byte[] row = row(market);
        boolean kospi = market == KisStockMasterMarket.KOSPI;
        put(row, kospi ? 90 : 85, "Y");
        put(row, kospi ? 123 : 118, "?");
        if (!kospi) {
            put(row, 91, " ");
        }

        var record = parser.parse(market, content(row)).records().getFirst();

        assertThat(record.rawSpac()).isEqualTo("Y");
        assertThat(record.rawManagement()).isEqualTo("?");
        assertThat(record.rawInvestmentCaution()).isEqualTo(kospi ? null : " ");
        assertThat(record.rawSuspension()).isEqualTo("N");
        assertThat(record.rawLiquidation()).isEqualTo("N");
        assertThat(record.rawLine().getBytes(CP949)).isEqualTo(row);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Y", "N", " ", "y", "?"})
    void retainsRestrictionCodesWithoutTrimmingOrNormalizing(String value) {
        for (var market : KisStockMasterMarket.values()) {
            byte[] row = row(market);
            boolean kospi = market == KisStockMasterMarket.KOSPI;
            put(row, kospi ? 90 : 85, value);
            put(row, kospi ? 123 : 118, value);
            if (!kospi) {
                put(row, 91, value);
            }
            var record = parser.parse(market, content(row)).records().getFirst();

            assertThat(record.rawSpac()).isEqualTo(value);
            assertThat(record.rawManagement()).isEqualTo(value);
            assertThat(record.rawInvestmentCaution()).isEqualTo(kospi ? null : value);
            assertThat(record.rawLine().getBytes(CP949)).isEqualTo(row);
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void retainsSourceOrderAndAlphanumericIdentifiers(KisStockMasterMarket market) throws Exception {
        byte[] input = content(row(market),
                row(market, "0001A0", "KR70001A0001", "ALPHA"),
                row(market, "Q520100", "KRG520001006", "ETN"));
        var result = parser.parse(market, input);

        assertThat(result.records()).extracting("symbol").containsExactly("005930", "0001A0", "Q520100");
        assertThat(result.records()).extracting("lineNumber").containsExactly(1, 2, 3);
        var restored = new ByteArrayOutputStream();
        for (var record : result.records()) {
            restored.writeBytes(record.rawLine().getBytes(CP949));
            restored.write('\n');
        }
        assertThat(restored.toByteArray()).isEqualTo(input);
        assertThat(sha256(restored.toByteArray())).isEqualTo(result.inputSha256());
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void retainsUnknownCodesBlankFieldsAndUninterpretedDates(KisStockMasterMarket market) {
        byte[] row = row(market);
        boolean kospi = market == KisStockMasterMarket.KOSPI;
        put(row, 61, "ZZ");
        put(row, kospi ? 83 : 79, "9");
        put(row, kospi ? 219 : 214, "9");
        put(row, kospi ? 166 : 161, "NOTADATE");
        put(row, kospi ? 121 : 116, "?");
        put(row, kospi ? 122 : 117, "U");
        put(row, kospi ? 265 : 259, "        ");
        var record = parser.parse(market, content(row)).records().getFirst();

        assertThat(record.rawGroup()).isEqualTo("ZZ");
        assertThat(record.rawEtp()).isEqualTo("9");
        assertThat(record.rawPreferred()).isEqualTo("9");
        assertThat(record.rawListingDate()).isEqualTo("NOTADATE");
        assertThat(record.rawSuspension()).isEqualTo("?");
        assertThat(record.rawLiquidation()).isEqualTo("U");
        assertThat(record.rawBaseDate()).isEqualTo("        ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN", "PF", "EF"})
    void doesNotRejectObservedGroupsOrEtpCodes(String group) {
        byte[] row = row(KisStockMasterMarket.KOSPI);
        put(row, 61, group);
        put(row, 83, group.equals("PF") ? "5" : "8");
        var record = parser.parse(KisStockMasterMarket.KOSPI, content(row)).records().getFirst();

        assertThat(record.rawGroup()).isEqualTo(group);
        assertThat(record.rawEtp()).isEqualTo(group.equals("PF") ? "5" : "8");
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void separatesFortyByteKoreanNameFromTheAsciiTail(KisStockMasterMarket market) {
        String name = "\uac00".repeat(20);
        var record = parser.parse(market, content(row(market, "005930", "KR7005930003", name)))
                .records().getFirst();

        assertThat(record.name()).isEqualTo(name);
        assertThat(record.rawGroup()).isEqualTo("ST");
        assertThat(record.rawEtp()).isEqualTo(" ");
    }

    @Test
    void removesOnlyRightAsciiPaddingFromDisplayFields() {
        byte[] row = row(KisStockMasterMarket.KOSPI, "005930", "KR7005930003", " " + NAME);
        var record = parser.parse(KisStockMasterMarket.KOSPI, content(row)).records().getFirst();

        assertThat(record.name()).isEqualTo(" " + NAME);
        assertThat(record.rawLine().substring(0, 9)).isEqualTo("005930   ");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void rejectsWrongRowLengthWithoutReturningPartialResults(int difference) {
        byte[] second = Arrays.copyOf(row(KisStockMasterMarket.KOSPI), 288 + difference);
        if (difference > 0) {
            second[288] = ' ';
        }
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI,
                content(row(KisStockMasterMarket.KOSPI), second)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("market=KOSPI", "line=2");
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void rejectsTheOtherMarketsLayout(KisStockMasterMarket market) {
        var other = market == KisStockMasterMarket.KOSPI ? KisStockMasterMarket.KOSDAQ : KisStockMasterMarket.KOSPI;
        assertThatThrownBy(() -> parser.parse(market, content(row(other))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("market=" + market, "line=1");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsEmptyRowsInsteadOfSkippingThem(int firstRowCount) {
        byte[] input = firstRowCount == 0 ? content(new byte[0]) : content(row(KisStockMasterMarket.KOSPI), new byte[0]);
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line=" + (firstRowCount + 1), "payload bytes=0");
    }

    @Test
    void rejectsMissingFinalLfAndCrlf() {
        byte[] row = row(KisStockMasterMarket.KOSPI);
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, row))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line=1", "LF terminator");
        byte[] crlf = Arrays.copyOf(row, row.length + 2);
        crlf[row.length] = '\r';
        crlf[row.length + 1] = '\n';
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, crlf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line=1", "CR is not supported");
    }

    @ParameterizedTest
    @ValueSource(ints = {21, 60})
    void rejectsMalformedCp949RatherThanReplacingBytes(int offset) {
        byte[] row = row(KisStockMasterMarket.KOSPI);
        row[offset] = (byte) 0x81;
        row[offset + 1] = ' ';
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, content(row)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line=1", "Invalid CP949 bytes");
    }

    @Test
    void rejectsMultibyteCharacterCrossingTheNameBoundary() {
        byte[] row = row(KisStockMasterMarket.KOSPI);
        row[60] = (byte) 0xb0;
        row[61] = (byte) 0xa1;
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, content(row)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid CP949 bytes");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9, 84})
    void rejectsControlBytesInIdentifiersAndTail(int offset) {
        byte[] row = row(KisStockMasterMarket.KOSPI);
        row[offset] = '\t';
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, content(row)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line=1", "printable ASCII");
    }

    @Test
    void rejectsNonAsciiTailAndControlCharactersInName() {
        byte[] row = row(KisStockMasterMarket.KOSPI);
        row[84] = (byte) 0xb0;
        row[85] = (byte) 0xa1;
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, content(row)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("printable ASCII");
        byte[] control = row(KisStockMasterMarket.KOSPI, "005930", "KR7005930003", "ASCII");
        control[21] = 0;
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, content(control)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Name must not contain control");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void rejectsBlankIdentifiers(int offset) {
        byte[] row = row(KisStockMasterMarket.KOSPI);
        Arrays.fill(row, offset, offset + (offset == 0 ? 9 : 12), (byte) ' ');
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, content(row)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line=1", "identifiers must not be blank");
    }

    @Test
    void rejectsDuplicateSymbolsAndStandardCodesWithBothLineNumbers() {
        var market = KisStockMasterMarket.KOSPI;
        assertThatThrownBy(() -> parser.parse(market,
                content(row(market), row(market, "005930", "KR7005931001", "DUPLICATE"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line=2", "Duplicate symbol", "firstLine=1");
        assertThatThrownBy(() -> parser.parse(market,
                content(row(market), row(market, "005935", "KR7005930003", "DUPLICATE"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line=2", "Duplicate standardCode", "firstLine=1");
    }

    @Test
    void keepsResultsIndependentOfLaterInputMutationsAndPreviousCalls() throws Exception {
        var market = KisStockMasterMarket.KOSPI;
        byte[] input = content(row(market));
        String hash = sha256(input);
        var result = parser.parse(market, input);
        Arrays.fill(input, (byte) 0);
        var second = parser.parse(market, content(row(market, "005935", "KR7005931001", "OTHER")));

        assertThat(result.inputSha256()).isEqualTo(hash);
        assertThat(result.records().getFirst().symbol()).isEqualTo("005930");
        assertThat(second.records().getFirst().symbol()).isEqualTo("005935");
    }

    @Test
    void rejectsMissingEmptyAndOversizedInput() {
        assertThatThrownBy(() -> parser.parse(null, new byte[0])).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("market=KOSPI", "line=1", "nonempty");
        assertThatThrownBy(() -> parser.parse(KisStockMasterMarket.KOSPI, new byte[KisStockMasterParser.MAX_CONTENT_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most 32 MiB");
    }

    private static String sha256(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }
}
