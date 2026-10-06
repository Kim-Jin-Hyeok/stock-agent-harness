CREATE TABLE kis_stock_basic_info_observation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requested_symbol VARCHAR(6) NOT NULL,
    http_status INT NOT NULL,
    request_started_at DATETIME(6) NOT NULL,
    response_received_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    content_length INT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    raw_content LONGBLOB NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
