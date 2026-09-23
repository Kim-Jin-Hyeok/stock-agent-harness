package com.stock.broker.order.inquiry.provider;

import com.stock.broker.order.inquiry.BrokerOrderInquiryRequest;
import com.stock.broker.order.inquiry.BrokerOrderInquiryResult;

public interface BrokerOrderInquiryProvider {
    BrokerOrderInquiryResult inquire(BrokerOrderInquiryRequest request);
}
