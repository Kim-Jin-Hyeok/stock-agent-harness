package com.stock.market.stock.master.provider.krx.parsing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;
import com.stock.market.stock.master.provider.krx.parsing.result.KrxStockBasicInfoParseResult;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;

public class KrxStockBasicInfoParser {
    public static final String PARSER_VERSION = "KRX_STOCK_BASIC_INFO_RAW_V1";
    public static final int MAX_CONTENT_BYTES = 32 * 1024 * 1024;
    private static final int FIELD_COUNT = 12;
    private static final ObjectReader JSON_READER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).reader();

    public KrxStockBasicInfoParseResult parse(byte[] content) {
        Objects.requireNonNull(content, "content must not be null.");
        if (content.length == 0 || content.length > MAX_CONTENT_BYTES) {
            throw failure("Content must be nonempty and at most 32 MiB.");
        }
        byte[] input = content.clone();
        JsonNode root;
        try {
            root = JSON_READER.readTree(input);
        } catch (IOException exception) {
            // Parser exception text can contain response values; do not expose it or attach it as a cause.
            throw failure("Invalid JSON content.");
        }
        if (root == null || !root.isObject() || root.size() != 1) {
            throw failure("Root must be an object containing only OutBlock_1.");
        }
        JsonNode rows = root.get("OutBlock_1");
        if (rows == null || !rows.isArray()) {
            throw failure("OutBlock_1 must be an array.");
        }
        var records = new ArrayList<KrxStockBasicInfoRawRecord>();
        for (int index = 0; index < rows.size(); index++) {
            records.add(parseRow(rows.get(index), index + 1));
        }
        return new KrxStockBasicInfoParseResult(sha256(input), PARSER_VERSION, records);
    }

    private static KrxStockBasicInfoRawRecord parseRow(JsonNode row, int rowNumber) {
        if (!row.isObject() || row.size() != FIELD_COUNT) {
            throw failure("Row must be an object with exactly 12 string fields. row=" + rowNumber + ".");
        }
        // Only structure is verified. Blank/unknown strings and duplicate stock identifiers remain raw.
        return new KrxStockBasicInfoRawRecord(rowNumber,
                string(row, "ISU_CD", rowNumber),
                string(row, "ISU_SRT_CD", rowNumber),
                string(row, "ISU_NM", rowNumber),
                string(row, "ISU_ABBRV", rowNumber),
                string(row, "ISU_ENG_NM", rowNumber),
                string(row, "LIST_DD", rowNumber),
                string(row, "MKT_TP_NM", rowNumber),
                string(row, "SECUGRP_NM", rowNumber),
                string(row, "SECT_TP_NM", rowNumber),
                string(row, "KIND_STKCERT_TP_NM", rowNumber),
                string(row, "PARVAL", rowNumber),
                string(row, "LIST_SHRS", rowNumber));
    }

    private static String string(JsonNode row, String field, int rowNumber) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual()) {
            throw failure("Field must be present as a JSON string. row=" + rowNumber + ", field=" + field + ".");
        }
        return value.textValue();
    }

    private static IllegalArgumentException failure(String reason) {
        return new IllegalArgumentException("KRX stock basic info parsing failed. cause=" + reason);
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }
}
