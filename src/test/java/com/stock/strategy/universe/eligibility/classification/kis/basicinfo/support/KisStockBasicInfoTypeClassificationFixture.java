package com.stock.strategy.universe.eligibility.classification.kis.basicinfo.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;

import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.content;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.output;

public final class KisStockBasicInfoTypeClassificationFixture {
    private static final KisStockBasicInfoParser PARSER = new KisStockBasicInfoParser();

    private KisStockBasicInfoTypeClassificationFixture() {
    }

    public static ObjectNode fields(String market, String productType, String group, String kind) {
        return output().put("mket_id_cd", market).put("prdt_type_cd", productType)
                .put("scty_grp_id_cd", group).put("stck_kind_cd", kind);
    }

    public static KisStockBasicInfoParseResult parsed(String market, String productType, String group, String kind) {
        return parsed(fields(market, productType, group, kind));
    }

    public static KisStockBasicInfoParseResult parsed(ObjectNode output) {
        return PARSER.parse(content(output));
    }
}
