package com.stock.market.price.history.query;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DailyPriceHistoryQueryService {
    private final DailyPriceBarRepository repository;

    public DailyPriceHistoryQueryService(
            DailyPriceBarRepository repository
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository must not be null."
        );
    }

    public DailyPriceHistory getDailyPriceHistory(
            DailyPriceHistoryRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        List<DailyPriceBar> bars = repository
                .findAllBySymbolAndTradingDateBetweenOrderByTradingDateAsc(
                        request.symbol(),
                        request.fromDate(),
                        request.toDate()
                )
                .stream()
                .map(entity -> entity.toBar())
                .toList();

        return new DailyPriceHistory(request.symbol(), bars);
    }
}
