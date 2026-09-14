package com.stock.harness.persistence;

import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;

public record HarnessToolRequestSnapshot(
        HarnessToolType type,
        String symbol
) {
    public static HarnessToolRequestSnapshot from(HarnessToolRequest request) {
        return new HarnessToolRequestSnapshot(
                request.type(),
                request.symbol()
        );
    }
}
