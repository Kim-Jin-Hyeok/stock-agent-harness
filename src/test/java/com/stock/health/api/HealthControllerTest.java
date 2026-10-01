package com.stock.health.api;

import com.stock.broker.account.provider.BrokerAccountProvider;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.initialization.StrategyPortfolioInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {
    private static final String SENSITIVE_DETAILS =
            "jdbc:mysql://private-db:3306/investment?user=private-user&password=test-db-secret; "
                    + "appKey=test-app-key; appSecret=test-app-secret; account=test-account";
    private static final String UP_RESPONSE = """
            {"status":"UP","app":"UP","db":"UP"}
            """;
    private static final String DOWN_RESPONSE = """
            {"status":"DOWN","app":"UP","db":"DOWN"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private BrokerAccountProvider brokerAccountProvider;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private StrategyPortfolioInitializer portfolioInitializer;

    private Connection connection;

    @BeforeEach
    void setUp() {
        connection = mock(Connection.class);
    }

    @AfterEach
    void doesNotQueryBrokerOrInitializePortfolio() {
        verifyNoInteractions(brokerAccountProvider, portfolioService, portfolioInitializer);
    }

    @Test
    void returnsUpAndClosesValidConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        expectDatabaseUp();

        verify(dataSource).getConnection();
        verify(connection).isValid(2);
        verify(connection).close();
        verifyNoMoreInteractions(dataSource, connection);
    }

    @Test
    void returnsUpForReachableDatabase() throws Exception {
        when(dataSource.getConnection()).thenAnswer(
                invocation -> DriverManager.getConnection("jdbc:h2:mem:health-test")
        );

        expectDatabaseUp();

        verify(dataSource).getConnection();
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    void returnsServiceUnavailableForInvalidConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        expectDatabaseDown();

        verify(connection).isValid(2);
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }

    @Test
    void connectionFailureDoesNotExposeExceptionDetails() throws Exception {
        when(dataSource.getConnection()).thenThrow(
                new SQLException(SENSITIVE_DETAILS, new IllegalStateException(SENSITIVE_DETAILS))
        );

        expectDatabaseDown();

        verify(dataSource).getConnection();
        verifyNoMoreInteractions(dataSource);
        verifyNoInteractions(connection);
    }

    @Test
    void validationFailureDoesNotExposeExceptionDetailsAndClosesConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenThrow(new SQLException(SENSITIVE_DETAILS));

        expectDatabaseDown();

        verify(connection).isValid(2);
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }

    @Test
    void closedDataSourceDoesNotExposeExceptionDetails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new IllegalStateException(SENSITIVE_DETAILS));

        expectDatabaseDown();

        verifyNoInteractions(connection);
    }

    @Test
    void connectionCloseFailureDoesNotExposeExceptionDetails() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        doThrow(new SQLException(SENSITIVE_DETAILS)).when(connection).close();

        expectDatabaseDown();

        verify(connection).isValid(2);
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }

    private void expectDatabaseUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(UP_RESPONSE, JsonCompareMode.STRICT));
    }

    private void expectDatabaseDown() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(DOWN_RESPONSE, JsonCompareMode.STRICT));
    }
}
