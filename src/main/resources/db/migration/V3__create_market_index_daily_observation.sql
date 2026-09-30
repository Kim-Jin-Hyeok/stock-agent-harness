CREATE TABLE market_index_daily_observation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    benchmark_id VARCHAR(30) NOT NULL,
    observation_date DATE NOT NULL,
    close_value DECIMAL(19, 6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_market_index_daily_observation_id_date UNIQUE (
        benchmark_id,
        observation_date
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
