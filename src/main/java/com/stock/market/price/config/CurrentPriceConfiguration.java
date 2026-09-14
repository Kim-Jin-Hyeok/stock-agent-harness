package com.stock.market.price.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CurrentPriceConfiguration {

    @Bean
    public Clock currentPriceClock() {
        return Clock.systemUTC();
    }
}
