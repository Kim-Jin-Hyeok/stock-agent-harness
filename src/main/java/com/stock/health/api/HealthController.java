package com.stock.health.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/health")
public class HealthController {
    private static final int DATABASE_VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        boolean databaseConnected = isDatabaseConnected();
        String status = databaseConnected ? "UP" : "DOWN";

        return ResponseEntity
                .status(databaseConnected ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(status, "UP", status));
    }

    private boolean isDatabaseConnected() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException | RuntimeException ignored) {
            return false;
        }
    }

    public record HealthResponse(String status, String app, String db) {
    }
}
