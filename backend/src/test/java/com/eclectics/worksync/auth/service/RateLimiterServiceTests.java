package com.eclectics.worksync.auth.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTests {
    @Test
    void acceptsFiveRequestsThenRejectsTheSixthPerIp() {
        var service = new RateLimiterService(5, 900);
        for (int i = 0; i < 5; i++) assertThat(service.isIpAllowed("192.0.2.1")).isTrue();
        assertThat(service.isIpAllowed("192.0.2.1")).isFalse();
        assertThat(service.isIpAllowed("192.0.2.2")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredWindowEntriesAreEvictedWithoutWaiting() {
        var service = new RateLimiterService(1, 900);
        service.isIpAllowed("192.0.2.1");
        var log = (Map<String, ConcurrentLinkedQueue<Instant>>)
                ReflectionTestUtils.getField(service, "ipRequestLog");
        log.get("192.0.2.1").clear();
        log.get("192.0.2.1").add(Instant.now().minusSeconds(901));
        assertThat(service.isIpAllowed("192.0.2.1")).isTrue();
        assertThat(service.isIpAllowed("192.0.2.1")).isFalse();
    }
}
