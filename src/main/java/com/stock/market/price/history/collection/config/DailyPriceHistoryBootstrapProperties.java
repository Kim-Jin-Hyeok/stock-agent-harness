package com.stock.market.price.history.collection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(
        prefix = "market.price.history.collection.bootstrap"
)
public record DailyPriceHistoryBootstrapProperties(
        boolean enabled,
        List<String> symbols,
        int initialLookbackYears
) {
    public DailyPriceHistoryBootstrapProperties {
        symbols = List.copyOf(Objects.requireNonNull(
                symbols,
                "Daily price history bootstrap symbols must not be null."
        ));
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException(
                    "Daily price history bootstrap symbols must not be empty."
            );
        }

        Set<String> uniqueSymbols = new HashSet<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "Daily price history bootstrap symbol must not be blank."
                );
            }
            if (!uniqueSymbols.add(symbol)) {
                throw new IllegalArgumentException(
                        "Duplicate daily price history bootstrap symbol: "
                                + symbol
                );
            }
        }

        if (initialLookbackYears <= 0) {
            throw new IllegalArgumentException(
                    "initialLookbackYears must be positive."
            );
        }
    }
}
