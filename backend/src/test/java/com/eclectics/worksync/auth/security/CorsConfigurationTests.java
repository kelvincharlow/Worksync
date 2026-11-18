package com.eclectics.worksync.auth.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CorsConfigurationTests {
    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.vercel.app", "null", "https://app.example/path",
            "https://app.example/", "https://app.example?query=value", "https://user@app.example", "not an origin"})
    void invalidOrWildcardOriginsFailClosed(String origin) {
        var config = new SecurityConfig(mock(JwtFilter.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", origin);
        assertThatThrownBy(config::corsConfigurationSource).isInstanceOf(IllegalArgumentException.class);
    }
}
