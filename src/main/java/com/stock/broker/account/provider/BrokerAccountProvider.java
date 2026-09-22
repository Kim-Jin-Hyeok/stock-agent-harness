package com.stock.broker.account.provider;

import com.stock.broker.account.BrokerAccountSnapshot;

public interface BrokerAccountProvider {
    BrokerAccountSnapshot getAccountSnapshot();
}
