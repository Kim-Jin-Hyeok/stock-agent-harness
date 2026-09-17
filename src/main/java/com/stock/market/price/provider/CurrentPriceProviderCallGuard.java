package com.stock.market.price.provider;

@FunctionalInterface
public interface CurrentPriceProviderCallGuard {
    void beforeCall();
}
