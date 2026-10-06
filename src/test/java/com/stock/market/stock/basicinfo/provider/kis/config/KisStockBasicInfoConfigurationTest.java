package com.stock.market.stock.basicinfo.provider.kis.config;

import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.config.KisConfiguration;
import com.stock.broker.kis.order.KisCashOrderClient;
import com.stock.broker.kis.order.cancellation.KisOrderCancellationClient;
import com.stock.broker.order.cancellation.provider.BrokerOrderCancellationProvider;
import com.stock.broker.order.provider.BrokerOrderProvider;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.config.support.KisStockBasicInfoMockHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KisStockBasicInfoConfigurationTest {
    private static final String PREFIX = "market.stock.basic-info.kis.";
    private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
    private static final String REST_CLIENT = "kisStockBasicInfoRestClient";
    private static final String APP_KEY = "synthetic-readonly-key";
    private static final String APP_SECRET = "synthetic-readonly-secret";
    private static final String ACCESS_TOKEN = "synthetic-readonly-token";
    private final KisStockBasicInfoMockHttp http = new KisStockBasicInfoMockHttp();
    private final ApplicationContextRunner runner = newRunner()
            .withInitializer(context -> context.getBeanFactory().addBeanPostProcessor(http));

    @Test
    void absentAndExplicitlyDisabledSettingsDoNotCreateClientsOrRequireKeys() {
        runner.run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false").run(this::assertDisabled);
        runner.withUserConfiguration(PropertiesScanConfiguration.class).run(context -> {
            assertDisabled(context);
            assertThat(context).hasSingleBean(KisStockBasicInfoProperties.class);
            assertThat(context.getBean(KisStockBasicInfoProperties.class).enabled()).isFalse();
        });
        http.verify();
    }

    @Test
    void applicationDefaultsLeaveBothConnectionsDisabled() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(PREFIX + "app-key=", PREFIX + "app-secret=")
                .run(context -> {
                    assertDisabled(context);
                    assertThat(context.getEnvironment().getProperty(PREFIX + "enabled")).isEqualTo("false");
                    assertThat(context.getEnvironment().getProperty("broker.kis.enabled")).isEqualTo("false");
                });
        http.verify();
    }

    @Test
    void createsSingletonReadOnlyBeansAndBindsDefaultsWithoutIssuingTokensOrCreatingOrders() {
        withSettings(settings()).withUserConfiguration(PropertiesScanConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(KisStockBasicInfoClient.class)
                    .hasSingleBean(KisStockBasicInfoProvider.class)
                    .hasSingleBean(KisTokenClient.class).hasSingleBean(KisTokenProvider.class).hasSingleBean(HttpClient.class);
            assertNoOrderBeans(context);
            var properties = context.getBean(KisStockBasicInfoProperties.class);
            assertThat(properties.appKey()).isEqualTo(APP_KEY);
            assertThat(properties.appSecret()).isEqualTo(APP_SECRET);
            assertThat(properties.tokenRefreshBeforeExpiration()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(15));
            var httpClient = context.getBean("kisStockBasicInfoHttpClient", HttpClient.class);
            assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(5));
            assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
            assertThat(httpClient.authenticator()).isEmpty();
            assertThat(context.getBean(KisStockBasicInfoClient.class))
                    .isSameAs(context.getBean(KisStockBasicInfoClient.class));
            assertThat(context.getBean(KisTokenProvider.class)).isSameAs(context.getBean(KisTokenProvider.class));
            assertThat(context.getBean(KisStockBasicInfoProvider.class))
                    .isSameAs(context.getBean(KisStockBasicInfoProvider.class));
            http.verify();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "app-key;appKey must be nonblank visible ASCII without whitespace.",
            "app-secret;appSecret must be nonblank visible ASCII without whitespace."
    }, delimiter = ';')
    void rejectsMissingCredentialsWithoutLeakingTheOtherCredential(String missing, String message) {
        var values = settings();
        values.remove(missing);
        withSettings(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(message);
            assertNoSensitiveOutput(context.getStartupFailure());
        });
        http.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "token-refresh-before-expiration=-1s", "connect-timeout=0s", "connect-timeout=-1s",
            "request-timeout=0s", "request-timeout=-1s", "connect-timeout=16s",
            "connect-timeout=1ns", "request-timeout=1ns",
            "app-key=synthetic-readonly-key invalid", "app-secret=synthetic-readonly-secret invalid"
    })
    void rejectsInvalidSettingsBeforeCreatingAnyRequest(String setting) {
        var values = settings();
        String[] parts = setting.split("=", 2);
        values.put(parts[0], parts[1]);
        withSettings(values).run(context -> {
            assertThat(context).hasFailed();
            assertNoSensitiveOutput(context.getStartupFailure());
        });
        http.verify();
    }

    @Test
    void sendsLiveTokenAndBasicInfoRequestsOnlyWhenExplicitlyCalledAndReusesTheToken() {
        withSettings(settings()).run(context -> {
            assertThat(context).hasNotFailed();
            http.verify();
            var server = http.server(REST_CLIENT);
            server.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                    .andExpect(method(POST))
                    .andExpect(content().json("""
                            {"grant_type":"client_credentials","appkey":"synthetic-readonly-key",
                             "appsecret":"synthetic-readonly-secret"}
                            """))
                    .andRespond(withSuccess("""
                            {"access_token":"synthetic-readonly-token","token_type":"Bearer",
                             "expires_in":3600,"access_token_token_expired":"2026-10-06 11:00:00"}
                            """, MediaType.APPLICATION_JSON));
            String body = "{\"synthetic\":\"basic-info\"}";
            String[] symbols = {"0004Y0", "005930"};
            for (String symbol : symbols) {
                server.expect(requestTo(BASE_URL
                                + "/uapi/domestic-stock/v1/quotations/search-stock-info?PRDT_TYPE_CD=300&PDNO=" + symbol))
                        .andExpect(method(GET))
                        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                        .andExpect(header("appkey", APP_KEY)).andExpect(header("appsecret", APP_SECRET))
                        .andExpect(header("tr_id", "CTPF1002R"))
                        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            }
            var provider = context.getBean(KisStockBasicInfoProvider.class);
            for (String symbol : symbols) {
                var response = provider.getStockBasicInfo(symbol);
                assertThat(response.requestedSymbol()).isEqualTo(symbol);
                assertThat(response.requestStartedAt()).isEqualTo(Instant.parse("2026-10-06T01:00:00Z"));
                assertThat(response.responseReceivedAt()).isEqualTo(response.requestStartedAt());
                assertThat(new String(response.content(), StandardCharsets.UTF_8)).isEqualTo(body);
            }
            http.verify();
        });
    }

    @Test
    void rejectsInvalidSymbolThroughRegisteredProviderBeforeAnyHttpRequest() {
        withSettings(settings()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(KisStockBasicInfoProvider.class).getStockBasicInfo(" 005930"))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.");
            http.verify();
        });
    }

    @Test
    void propagatesSafeTokenFailureThroughRegisteredProviderWithoutBasicInfoRequestOrRetry() {
        withSettings(settings()).run(context -> {
            assertThat(context).hasNotFailed();
            http.server(REST_CLIENT).expect(requestTo(BASE_URL + "/oauth2/tokenP"))
                    .andExpect(method(POST))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .body(APP_KEY + APP_SECRET + ACCESS_TOKEN));
            assertThatThrownBy(() -> context.getBean(KisStockBasicInfoProvider.class).getStockBasicInfo("005930"))
                    .isExactlyInstanceOf(RestClientResponseException.class)
                    .hasMessage("KIS token response HTTP status=429.").hasNoCause()
                    .satisfies(failure -> {
                        assertThat(((RestClientResponseException) failure).getStatusCode().value()).isEqualTo(429);
                        assertThat(((RestClientResponseException) failure).getResponseBodyAsByteArray()).isEmpty();
                        assertNoSensitiveOutput(failure);
                    });
            http.verify();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "BASIC_INFO"})
    @Timeout(10)
    void cancelsUnresponsiveNativeRequestsAtTheConfiguredTimeoutWithoutNetworkAccess(String requestType) {
        HttpClient nativeClient = mock(HttpClient.class);
        CompletableFuture<HttpResponse<InputStream>> pendingResponse = new CompletableFuture<>();
        when(nativeClient.<InputStream>sendAsync(any(HttpRequest.class), any())).thenReturn(pendingResponse);
        var nativeRunner = newRunner().withInitializer(context -> context.getBeanFactory()
                .addBeanPostProcessor(new BeanPostProcessor() {
                    @Override
                    public Object postProcessAfterInitialization(Object bean, String beanName) {
                        if (beanName.equals("kisStockBasicInfoHttpClient")) {
                            ((HttpClient) bean).shutdownNow();
                            return nativeClient;
                        }
                        return bean;
                    }
                }));
        withSettings(nativeRunner, settings())
                .withPropertyValues(PREFIX + "connect-timeout=20ms", PREFIX + "request-timeout=50ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    verify(nativeClient, never()).sendAsync(any(HttpRequest.class), any());
                    if (requestType.equals("TOKEN")) {
                        assertThatThrownBy(context.getBean(KisTokenClient.class)::issueToken)
                                .isExactlyInstanceOf(ResourceAccessException.class)
                                .hasMessage("KIS token request failed.").hasNoCause();
                    } else {
                        assertThatThrownBy(() -> context.getBean(KisStockBasicInfoClient.class)
                                .getStockBasicInfo("005930", ACCESS_TOKEN))
                                .isExactlyInstanceOf(IllegalStateException.class)
                                .hasMessage("KIS stock basic info request failed.").hasNoCause();
                    }
                    var captor = ArgumentCaptor.forClass(HttpRequest.class);
                    verify(nativeClient).sendAsync(captor.capture(), any());
                    assertThat(pendingResponse.isCancelled()).isTrue();
                    assertThat(context.getBean(KisStockBasicInfoProperties.class).requestTimeout())
                            .isEqualTo(Duration.ofMillis(50));
                    String path = requestType.equals("TOKEN") ? "/oauth2/tokenP"
                            : "/uapi/domestic-stock/v1/quotations/search-stock-info?PRDT_TYPE_CD=300&PDNO=005930";
                    assertThat(captor.getValue().uri().toString()).isEqualTo(BASE_URL + path);
                });
    }

    private ApplicationContextRunner newRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(KisStockBasicInfoConfiguration.class, KisConfiguration.class)
                .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-10-06T01:00:00Z"), ZoneOffset.UTC));
    }

    private Map<String, String> settings() {
        var values = new LinkedHashMap<String, String>();
        values.put("enabled", "true");
        values.put("app-key", APP_KEY);
        values.put("app-secret", APP_SECRET);
        return values;
    }

    private ApplicationContextRunner withSettings(Map<String, String> values) {
        return withSettings(runner, values);
    }

    private ApplicationContextRunner withSettings(ApplicationContextRunner contextRunner, Map<String, String> values) {
        return contextRunner.withPropertyValues(values.entrySet().stream()
                .map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }

    private void assertDisabled(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().doesNotHaveBean(KisStockBasicInfoClient.class)
                .doesNotHaveBean(KisStockBasicInfoProvider.class)
                .doesNotHaveBean(KisTokenClient.class).doesNotHaveBean(KisTokenProvider.class).doesNotHaveBean(HttpClient.class);
        assertNoOrderBeans(context);
    }

    private void assertNoOrderBeans(AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(KisCashOrderClient.class).doesNotHaveBean(KisOrderCancellationClient.class)
                .doesNotHaveBean(BrokerOrderProvider.class).doesNotHaveBean(BrokerOrderCancellationProvider.class);
    }

    private void assertNoSensitiveOutput(Throwable failure) {
        StringWriter stackTrace = new StringWriter();
        failure.printStackTrace(new PrintWriter(stackTrace));
        assertThat(stackTrace.toString()).doesNotContain(APP_KEY, APP_SECRET, ACCESS_TOKEN);
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = KisStockBasicInfoProperties.class)
    static class PropertiesScanConfiguration {
    }
}
