package com.eclectics.worksync.auth.service;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Reflection advances private expiry fixtures without sleeping or altering production clocks. */
class PasswordResetTokenServiceTests {
    @Test
    void normalizesEmailAndConsumesCode() {
        var service = new PasswordResetTokenService();
        String code = service.createResetCode(" USER@example.com ");
        assertThat(service.validateCode("user@EXAMPLE.com", code)).isTrue();
        service.consumeCode(" User@Example.com ");
        assertThat(service.validateCode("user@example.com", code)).isFalse();
    }

    @Test
    void fiveFailedAttemptsInvalidateEvenTheCorrectCode() {
        var service = new PasswordResetTokenService();
        String code = service.createResetCode("user@example.com");
        for (int i = 0; i < 5; i++) {
            assertThat(service.validateCode("user@example.com", "000000")).isFalse();
        }
        assertThat(service.validateCode("user@example.com", code)).isFalse();
    }

    @Test
    void expiredCodeIsRejectedAndRemoved() throws Exception {
        var service = new PasswordResetTokenService();
        String code = service.createResetCode("user@example.com");
        expire(service, "user@example.com");
        assertThat(service.validateCode("user@example.com", code)).isFalse();
        assertThat(store(service).isEmpty()).isTrue();
    }

    @Test
    void issuingAnotherCodeResetsTheAttemptBudget() {
        var service = new PasswordResetTokenService();
        service.createResetCode("user@example.com");
        for (int i = 0; i < 5; i++) service.validateCode("user@example.com", "000000");
        String replacement = service.createResetCode("user@example.com");
        // Characterizes the known per-account throttling gap; not a security guarantee.
        assertThat(service.validateCode("user@example.com", replacement)).isTrue();
    }

    private static void expire(PasswordResetTokenService service, String email) throws Exception {
        Map<String, Object> store = store(service);
        Object entry = store.get(email);
        var constructor = entry.getClass().getDeclaredConstructor(String.class, Instant.class, int.class);
        constructor.setAccessible(true);
        var accessor = entry.getClass().getDeclaredMethod("hashedCode");
        accessor.setAccessible(true);
        store.put(email, constructor.newInstance(accessor.invoke(entry), Instant.now().minusSeconds(1), 0));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> store(PasswordResetTokenService service) {
        return (Map<String, Object>) ReflectionTestUtils.getField(service, "resetStore");
    }
}
