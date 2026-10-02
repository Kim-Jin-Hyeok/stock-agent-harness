package com.stock.market.price.history.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceBarMigrationTest {
    @Test
    void addsNullableMetadataWithoutChangingExistingOhlcv() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:daily-price-v4;MODE=MySQL")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V2__create_daily_price_bar.sql"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO daily_price_bar
                            (symbol, trading_date, open_price_krw, high_price_krw,
                             low_price_krw, close_price_krw, volume)
                        VALUES ('005930', '2026-09-23', 70000, 73000, 69000, 72000, 1000000)
                        """);
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V4__add_daily_price_trading_value.sql"));

            try (var statement = connection.createStatement();
                 var row = statement.executeQuery("SELECT * FROM daily_price_bar")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("id")).isEqualTo(1L);
                assertThat(row.getString("symbol")).isEqualTo("005930");
                assertThat(row.getDate("trading_date").toLocalDate().toString())
                        .isEqualTo("2026-09-23");
                assertThat(row.getLong("open_price_krw")).isEqualTo(70_000L);
                assertThat(row.getLong("high_price_krw")).isEqualTo(73_000L);
                assertThat(row.getLong("low_price_krw")).isEqualTo(69_000L);
                assertThat(row.getLong("close_price_krw")).isEqualTo(72_000L);
                assertThat(row.getLong("volume")).isEqualTo(1_000_000L);
                assertThat(row.getObject("trading_value_krw")).isNull();
                assertThat(row.getObject("trading_venue_scope")).isNull();
                assertThat(row.next()).isFalse();
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO daily_price_bar
                            (symbol, trading_date, open_price_krw, high_price_krw,
                             low_price_krw, close_price_krw, volume,
                             trading_value_krw, trading_venue_scope)
                        VALUES ('000660', '2026-09-23', 70000, 73000, 69000, 72000,
                                1000000, 0, 'INTEGRATED')
                        """);
                try (var row = statement.executeQuery(
                        "SELECT trading_value_krw, trading_venue_scope "
                                + "FROM daily_price_bar WHERE symbol = '000660'")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getObject("trading_value_krw")).isEqualTo(0L);
                    assertThat(row.getString("trading_venue_scope")).isEqualTo("INTEGRATED");
                }
            }
        }
    }
}
