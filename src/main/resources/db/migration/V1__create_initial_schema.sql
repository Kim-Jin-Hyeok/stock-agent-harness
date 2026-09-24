CREATE TABLE harness_run_entity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(255),
    strategy_id VARCHAR(255) NOT NULL,
    strategy_version INT NOT NULL,
    horizon VARCHAR(255) NOT NULL,
    status VARCHAR(255),
    started_at DATETIME(6),
    finished_at DATETIME(6),
    decision_snapshot_json LONGTEXT,
    risk_check_snapshot_json LONGTEXT,
    portfolio_snapshot_json LONGTEXT,
    market_snapshot_json LONGTEXT,
    tool_execution_snapshots_json LONGTEXT,
    candidate_symbols_json LONGTEXT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_harness_run_run_id (run_id),
    KEY idx_harness_run_strategy_started_at (
        strategy_id,
        strategy_version,
        horizon,
        started_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE harness_step_entity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(255),
    step_order INT,
    type VARCHAR(255),
    status VARCHAR(255),
    message VARCHAR(255),
    started_at DATETIME(6),
    finished_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_harness_step_run_order (run_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trade_record_entity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(255),
    action VARCHAR(255),
    symbol VARCHAR(255),
    quantity BIGINT,
    price_krw BIGINT,
    order_amount_krw BIGINT,
    status VARCHAR(255),
    reason_code VARCHAR(255),
    reason VARCHAR(255),
    executed_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_trade_record_run_id (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE strategy_portfolio (
    id BIGINT NOT NULL AUTO_INCREMENT,
    strategy_id VARCHAR(255) NOT NULL,
    strategy_version INT NOT NULL,
    horizon VARCHAR(255) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_strategy_portfolio_identity UNIQUE (
        strategy_id,
        strategy_version,
        horizon
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE broker_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    broker_order_id VARCHAR(100),
    broker_order_organization_number VARCHAR(100),
    run_id VARCHAR(100) NOT NULL,
    strategy_id VARCHAR(100) NOT NULL,
    strategy_version INT NOT NULL,
    horizon VARCHAR(30) NOT NULL,
    side VARCHAR(10) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    requested_quantity BIGINT NOT NULL,
    limit_price_krw BIGINT NOT NULL,
    cumulative_filled_quantity BIGINT NOT NULL,
    cumulative_filled_amount_krw BIGINT NOT NULL,
    portfolio_applied_quantity BIGINT NOT NULL,
    portfolio_applied_amount_krw BIGINT NOT NULL,
    average_filled_price_krw BIGINT,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    submitted_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6),
    last_reconciled_at DATETIME(6),
    cancellation_status VARCHAR(30),
    cancellation_order_id VARCHAR(100),
    cancellation_order_organization_number VARCHAR(100),
    cancellation_submitted_at DATETIME(6),
    cancellation_reason VARCHAR(1000),
    PRIMARY KEY (id),
    KEY idx_broker_order_status_submitted_at (status, submitted_at),
    KEY idx_broker_order_run_id (run_id),
    KEY idx_broker_order_cancellation_target (
        status,
        cancellation_status,
        expires_at
    ),
    KEY idx_broker_order_active_lookup (
        strategy_id,
        strategy_version,
        horizon,
        symbol,
        side,
        status
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE current_price_observation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(100) NOT NULL,
    strategy_id VARCHAR(100) NOT NULL,
    strategy_version INT NOT NULL,
    horizon VARCHAR(30) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    price_krw BIGINT NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    source VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_current_price_observation_run_id (run_id),
    KEY idx_current_price_observation_strategy_symbol_time (
        strategy_id,
        strategy_version,
        horizon,
        symbol,
        observed_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
