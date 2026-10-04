CREATE TABLE stock_candidate_evaluation_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recorded_at DATETIME(6) NOT NULL,
    selection_as_of_date DATE NOT NULL,
    evaluation_status VARCHAR(20) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
