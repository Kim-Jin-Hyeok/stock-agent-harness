package com.stock.market.stock.basicinfo.observation.persistence;

import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.END;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.content;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoObservationMigrationTest {
    private static final String INSERT_SQL = """
            INSERT INTO kis_stock_basic_info_observation
                (requested_symbol, http_status, request_started_at, response_received_at, recorded_at,
                 content_length, content_sha256, raw_content)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Test
    void migrationSupportsFullSizeBlobAndRepeatedObservationsWithoutChangingExistingRows() throws Exception {
        try (Connection connection = migratedConnection()) {
            byte[] bytes = new byte[KisStockBasicInfoParser.MAX_CONTENT_BYTES];
            Arrays.fill(bytes, (byte) 0xff);
            for (int index = 0; index < 2; index++) {
                try (var statement = connection.prepareStatement(INSERT_SQL)) {
                    bind(statement, bytes);
                    statement.executeUpdate();
                }
            }

            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT * FROM kis_stock_basic_info_observation ORDER BY id")) {
                long previousId = 0;
                for (int index = 0; index < 2; index++) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isGreaterThan(previousId);
                    previousId = rows.getLong("id");
                    assertThat(rows.getString("requested_symbol")).isEqualTo(SYMBOL);
                    assertThat(rows.getInt("http_status")).isEqualTo(200);
                    assertThat(rows.getTimestamp("request_started_at").toInstant()).isEqualTo(START.truncatedTo(ChronoUnit.MICROS));
                    assertThat(rows.getTimestamp("response_received_at").toInstant()).isEqualTo(END.truncatedTo(ChronoUnit.MICROS));
                    assertThat(rows.getTimestamp("recorded_at").toInstant()).isEqualTo(RECORDED_AT.truncatedTo(ChronoUnit.MICROS));
                    assertThat(rows.getInt("content_length")).isEqualTo(bytes.length);
                    assertThat(rows.getString("content_sha256")).isEqualTo(sha256(bytes));
                    assertThat(rows.getBytes("raw_content")).isEqualTo(bytes);
                }
                assertThat(rows.next()).isFalse();
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT snapshot_json FROM stock_candidate_evaluation_snapshot")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("snapshot_json")).isEqualTo("legacy-payload");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void requiresAllRawObservationColumns(int nullColumn) throws Exception {
        try (Connection connection = migratedConnection();
             var statement = connection.prepareStatement(INSERT_SQL)) {
            bind(statement, content());
            statement.setObject(nullColumn, null);

            assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class)
                    .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("23502"));
        }
    }

    private void bind(PreparedStatement statement, byte[] content) throws SQLException {
        statement.setString(1, SYMBOL);
        statement.setInt(2, 200);
        statement.setTimestamp(3, Timestamp.from(START.truncatedTo(ChronoUnit.MICROS)));
        statement.setTimestamp(4, Timestamp.from(END.truncatedTo(ChronoUnit.MICROS)));
        statement.setTimestamp(5, Timestamp.from(RECORDED_AT.truncatedTo(ChronoUnit.MICROS)));
        statement.setInt(6, content.length);
        statement.setString(7, sha256(content));
        statement.setBytes(8, content);
    }

    private Connection migratedConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:kis-basic-info-v7-" + UUID.randomUUID() + ";MODE=MySQL");
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V6__create_stock_candidate_evaluation_snapshot.sql"));
            try (var statement = connection.prepareStatement("""
                    INSERT INTO stock_candidate_evaluation_snapshot
                        (recorded_at, selection_as_of_date, evaluation_status, snapshot_json)
                    VALUES (?, '2026-10-07', 'INCOMPLETE', 'legacy-payload')
                    """)) {
                statement.setTimestamp(1, Timestamp.from(RECORDED_AT.truncatedTo(ChronoUnit.MICROS)));
                statement.executeUpdate();
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V7__create_kis_stock_basic_info_observation.sql"));
            return connection;
        } catch (RuntimeException | SQLException failure) {
            connection.close();
            throw failure;
        }
    }
}
