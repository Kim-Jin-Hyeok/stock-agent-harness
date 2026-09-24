CREATE TABLE daily_price_bar (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(30) NOT NULL,
    trading_date DATE NOT NULL,
    open_price_krw BIGINT NOT NULL,
    high_price_krw BIGINT NOT NULL,
    low_price_krw BIGINT NOT NULL,
    close_price_krw BIGINT NOT NULL,
    volume BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_price_bar_symbol_date UNIQUE (symbol, trading_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
