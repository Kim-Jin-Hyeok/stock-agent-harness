ALTER TABLE daily_price_bar
    ADD COLUMN trading_value_krw BIGINT NULL;

ALTER TABLE daily_price_bar
    ADD COLUMN trading_venue_scope VARCHAR(30) NULL;
