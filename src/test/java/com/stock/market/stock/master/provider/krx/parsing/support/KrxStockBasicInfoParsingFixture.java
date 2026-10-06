package com.stock.market.stock.master.provider.krx.parsing.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.provider.krx.parsing.record.KrxStockBasicInfoRawRecord;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class KrxStockBasicInfoParsingFixture {
    public static final String NAME = "\ud569\uc131 \uc885\ubaa9";
    public static final List<String> FIELDS = List.of(
            "ISU_CD", "ISU_SRT_CD", "ISU_NM", "ISU_ABBRV", "ISU_ENG_NM", "LIST_DD",
            "MKT_TP_NM", "SECUGRP_NM", "SECT_TP_NM", "KIND_STKCERT_TP_NM", "PARVAL", "LIST_SHRS"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KrxStockBasicInfoParsingFixture() {
    }

    public static String[] values() {
        // Synthetic values test the published field shape, not actual KRX type definitions.
        return new String[]{"KR7005930003", "005930", NAME, "SYNTHETIC", "SYNTHETIC STOCK",
                "UNKNOWN_DATE", "UNVERIFIED_MARKET", "UNVERIFIED_GROUP", "-", "UNVERIFIED_KIND",
                "000100.00", "001234"};
    }

    public static ObjectNode row() {
        var row = MAPPER.createObjectNode();
        String[] values = values();
        for (int index = 0; index < FIELDS.size(); index++) {
            row.put(FIELDS.get(index), values[index]);
        }
        return row;
    }

    public static byte[] content(ObjectNode... rows) {
        var root = MAPPER.createObjectNode();
        var array = root.putArray("OutBlock_1");
        for (ObjectNode row : rows) {
            array.add(row);
        }
        return json(root.toString());
    }

    public static byte[] json(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static KrxStockBasicInfoRawRecord rawRecord(int rowNumber, String... values) {
        if (values.length != FIELDS.size()) {
            throw new IllegalArgumentException("Fixture must supply 12 values.");
        }
        return new KrxStockBasicInfoRawRecord(rowNumber, values[0], values[1], values[2], values[3], values[4], values[5],
                values[6], values[7], values[8], values[9], values[10], values[11]);
    }
}
