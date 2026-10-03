package com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionSnapshotMigrationTest {
    private static final Instant RECORDED_AT = Instant.parse("2026-10-03T09:00:00Z");
    private static final LocalDate SELECTION_DATE = LocalDate.of(2026, 9, 23);
    private static final String INSERT_SQL = """
            INSERT INTO daily_trading_value_selection_snapshot
                (recorded_at, selection_as_of_date, evaluation_status, snapshot_json)
            VALUES (?, ?, ?, ?)
            """;

    @Test
    void migrationSupportsLargePayloadAndMultipleEvaluationsForTheSameDate() throws Exception {
        try (Connection connection = migratedConnection()) {
            String largeJson = "{\"evidence\":\"" + "x".repeat(70_000) + "\"}";
            insert(connection, "INCOMPLETE", largeJson);
            insert(connection, "COMPLETE", "{}");

            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT * FROM daily_trading_value_selection_snapshot ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isPositive();
                long firstId = rows.getLong("id");
                assertThat(rows.getTimestamp("recorded_at").toInstant()).isEqualTo(RECORDED_AT);
                assertThat(rows.getDate("selection_as_of_date").toLocalDate()).isEqualTo(SELECTION_DATE);
                assertThat(rows.getString("evaluation_status")).isEqualTo("INCOMPLETE");
                assertThat(rows.getString("snapshot_json")).isEqualTo(largeJson);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isGreaterThan(firstId);
                assertThat(rows.getDate("selection_as_of_date").toLocalDate()).isEqualTo(SELECTION_DATE);
                assertThat(rows.getString("evaluation_status")).isEqualTo("COMPLETE");
                assertThat(rows.getString("snapshot_json")).isEqualTo("{}");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void migrationRequiresTimestampDateStatusAndJson(int nullColumn) throws Exception {
        try (Connection connection = migratedConnection();
             var statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setTimestamp(1, Timestamp.from(RECORDED_AT));
            statement.setDate(2, Date.valueOf(SELECTION_DATE));
            statement.setString(3, "INCOMPLETE");
            statement.setString(4, "{}");
            statement.setObject(nullColumn, null);

            assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23502"));
        }
    }

    private void insert(Connection connection, String status, String json) throws SQLException {
        try (var statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setTimestamp(1, Timestamp.from(RECORDED_AT));
            statement.setDate(2, Date.valueOf(SELECTION_DATE));
            statement.setString(3, status);
            statement.setString(4, json);
            statement.executeUpdate();
        }
    }

    private Connection migratedConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:selection-snapshot-v5-" + UUID.randomUUID() + ";MODE=MySQL"
        );
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V5__create_daily_trading_value_selection_snapshot.sql"
            ));
            return connection;
        } catch (RuntimeException error) {
            connection.close();
            throw error;
        }
    }
}
