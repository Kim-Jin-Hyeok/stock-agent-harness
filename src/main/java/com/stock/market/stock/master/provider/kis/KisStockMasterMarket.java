package com.stock.market.stock.master.provider.kis;

import java.net.URI;

public enum KisStockMasterMarket {
    KOSPI("kospi_code.mst"),
    KOSDAQ("kosdaq_code.mst");

    private final String fileName;

    KisStockMasterMarket(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }

    public URI sourceUri() {
        return URI.create("https://new.real.download.dws.co.kr/common/master/" + fileName + ".zip");
    }
}
