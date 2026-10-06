package com.stock.market.stock.basicinfo.provider.kis.config.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KisStockBasicInfoMockHttp implements BeanPostProcessor {
    private final Map<String, MockRestServiceServer> servers = new LinkedHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof RestClient restClient) {
            RestClient.Builder builder = restClient.mutate();
            servers.put(beanName, MockRestServiceServer.bindTo(builder).build());
            return builder.build();
        }
        return bean;
    }

    public MockRestServiceServer server(String beanName) {
        MockRestServiceServer server = servers.get(beanName);
        if (server == null) {
            throw new IllegalStateException("Mock HTTP server was not registered for bean " + beanName + ".");
        }
        return server;
    }

    public void verify() {
        servers.values().forEach(MockRestServiceServer::verify);
    }
}
