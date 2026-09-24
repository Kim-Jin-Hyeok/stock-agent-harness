package com.stock.market.price.history.persistence;

import com.stock.market.price.history.DailyPriceBar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "daily_price_bar",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_price_bar_symbol_date",
                columnNames = {"symbol", "trading_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPriceBarEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(name = "open_price_krw", nullable = false)
    private long openPriceKrw;

    @Column(name = "high_price_krw", nullable = false)
    private long highPriceKrw;

    @Column(name = "low_price_krw", nullable = false)
    private long lowPriceKrw;

    @Column(name = "close_price_krw", nullable = false)
    private long closePriceKrw;

    @Column(name = "volume", nullable = false)
    private long volume;

    public static DailyPriceBarEntity from(
            String symbol,
            DailyPriceBar bar
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(bar, "bar must not be null.");

        DailyPriceBarEntity entity = new DailyPriceBarEntity();
        entity.symbol = symbol;
        entity.tradingDate = bar.tradingDate();
        entity.openPriceKrw = bar.openPriceKrw();
        entity.highPriceKrw = bar.highPriceKrw();
        entity.lowPriceKrw = bar.lowPriceKrw();
        entity.closePriceKrw = bar.closePriceKrw();
        entity.volume = bar.volume();
        return entity;
    }

    public DailyPriceBar toBar() {
        return new DailyPriceBar(
                tradingDate,
                openPriceKrw,
                highPriceKrw,
                lowPriceKrw,
                closePriceKrw,
                volume
        );
    }
}
