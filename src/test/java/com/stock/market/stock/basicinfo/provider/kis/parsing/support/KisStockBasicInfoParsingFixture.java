package com.stock.market.stock.basicinfo.provider.kis.parsing.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.basicinfo.provider.kis.parsing.record.KisStockBasicInfoRawRecord;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class KisStockBasicInfoParsingFixture {
    public static final String NAME = "\ud569\uc131 \uc885\ubaa9";
    public static final String MESSAGE = "\uc870\ud68c\ub418\uc5c8\uc2b5\ub2c8\ub2e4  ";
    public static final List<String> FIELDS = List.of(
            "pdno", "std_pdno", "prdt_name", "prdt_type_cd", "mket_id_cd", "scty_grp_id_cd",
            "stck_kind_cd", "tr_stop_yn", "admn_item_yn", "scts_mket_lstg_dt", "scts_mket_lstg_abol_dt",
            "kosdaq_mket_lstg_dt", "kosdaq_mket_lstg_abol_dt", "lstg_abol_dt", "nxt_tr_stop_yn", "cptt_trad_tr_psbl_yn"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KisStockBasicInfoParsingFixture() {
    }

    public static String[] values() {
        // Synthetic values assert source preservation, not eligibility or code definitions.
        return new String[]{"00000A0004Y0", "KR70004Y0000", NAME, "300", "UNVERIFIED_MARKET", "UNVERIFIED_GROUP",
                "UNVERIFIED_KIND", "N", "Y", "UNKNOWN_KOSPI_DATE", "", "UNKNOWN_KOSDAQ_DATE", "", "", "N", "N"};
    }

    public static ObjectNode output() {
        var output = MAPPER.createObjectNode();
        String[] values = values();
        for (int index = 0; index < FIELDS.size(); index++) {
            output.put(FIELDS.get(index), values[index]);
        }
        return output;
    }

    public static ObjectNode root() {
        var root = MAPPER.createObjectNode();
        root.put("rt_cd", "0");
        root.put("msg_cd", "KIOK0530");
        root.put("msg1", MESSAGE);
        root.set("output", output());
        return root;
    }

    public static byte[] content(ObjectNode output) {
        var root = root();
        root.set("output", output);
        return json(root.toString());
    }

    public static byte[] json(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static KisStockBasicInfoRawRecord rawRecord(String... values) {
        if (values.length != FIELDS.size()) {
            throw new IllegalArgumentException("Fixture must supply 16 values.");
        }
        return new KisStockBasicInfoRawRecord(values[0], values[1], values[2], values[3], values[4], values[5],
                values[6], values[7], values[8], values[9], values[10], values[11], values[12], values[13], values[14], values[15]);
    }
}
