package com.stock.harness;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessPropertiesBindingTest {
    @Test
    void bindsAllHarnessLimitsToCanonicalConstructor() {
        MapConfigurationPropertySource source =
                new MapConfigurationPropertySource(Map.of(
                        "harness.max-steps", 10,
                        "harness.max-tool-calls", 5,
                        "harness.max-tool-retries", 1,
                        "harness.max-provider-calls", 3
                ));

        HarnessProperties properties = new Binder(source)
                .bind("harness", Bindable.of(HarnessProperties.class))
                .orElseThrow(() -> new AssertionError(
                        "Harness properties were not bound."
                ));

        assertThat(properties).isEqualTo(new HarnessProperties(10, 5, 1, 3));
    }
}
