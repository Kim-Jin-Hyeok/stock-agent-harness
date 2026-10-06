package com.stock.market.stock.basicinfo.provider.kis.parsing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.stock.market.stock.basicinfo.provider.kis.parsing.record.KisStockBasicInfoRawRecord;
import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public class KisStockBasicInfoParser {
    public static final String PARSER_VERSION = "KIS_STOCK_BASIC_INFO_RAW_V1";
    public static final int MAX_CONTENT_BYTES = 1024 * 1024;
    private static final ObjectReader JSON_READER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).reader();

    public KisStockBasicInfoParseResult parse(byte[] content) {
        Objects.requireNonNull(content, "content must not be null.");
        if (content.length == 0 || content.length > MAX_CONTENT_BYTES) {
            throw failure("Content must be nonempty and at most 1 MiB.");
        }
        byte[] input = content.clone();
        JsonNode root;
        try {
            root = JSON_READER.readTree(input);
        } catch (IOException exception) {
            // JSON exception text can contain response values; expose neither it nor its cause.
            throw failure("Invalid JSON content.");
        }
        if (root == null || !root.isObject()) {
            throw failure("Root must be an object.");
        }
        String responseCode = string(root, "rt_cd", "root");
        if (!"0".equals(responseCode)) {
            throw failure("rt_cd must be the success string 0.");
        }
        String messageCode = string(root, "msg_cd", "root");
        String message = string(root, "msg1", "root");
        JsonNode output = root.get("output");
        if (output == null || !output.isObject()) {
            throw failure("output must be a single object.");
        }
        // Selected fields are required strings. Extra API fields are not interpreted or discarded from the input hash.
        var record = new KisStockBasicInfoRawRecord(
                string(output, "pdno", "output"),
                string(output, "std_pdno", "output"),
                string(output, "prdt_name", "output"),
                string(output, "prdt_type_cd", "output"),
                string(output, "mket_id_cd", "output"),
                string(output, "scty_grp_id_cd", "output"),
                string(output, "stck_kind_cd", "output"),
                string(output, "tr_stop_yn", "output"),
                string(output, "admn_item_yn", "output"),
                string(output, "scts_mket_lstg_dt", "output"),
                string(output, "scts_mket_lstg_abol_dt", "output"),
                string(output, "kosdaq_mket_lstg_dt", "output"),
                string(output, "kosdaq_mket_lstg_abol_dt", "output"),
                string(output, "lstg_abol_dt", "output"),
                string(output, "nxt_tr_stop_yn", "output"),
                string(output, "cptt_trad_tr_psbl_yn", "output"));
        return new KisStockBasicInfoParseResult(sha256(input), PARSER_VERSION,
                responseCode, messageCode, message, record);
    }

    private static String string(JsonNode object, String field, String location) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw failure("Field must be present as a JSON string. location=" + location + ", field=" + field + ".");
        }
        return value.textValue();
    }

    private static IllegalArgumentException failure(String reason) {
        return new IllegalArgumentException("KIS stock basic info parsing failed. cause=" + reason);
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }
}
